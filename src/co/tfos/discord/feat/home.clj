(ns co.tfos.discord.feat.home
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.string :as str]
            [co.tfos.discord.ui :as ui]
            [xtdb.api :as xt]))

; "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

#_(defn atom-feed [{:keys [posts path site] :as opts}]
    (let [feed-url (str (:url site) path)
          posts (remove #(contains? (:tags %) "unlisted") posts)]
      [:feed {:xmlns "http://www.w3.org/2005/Atom"}
       [:title (:title site)]
       [:id (url-encode feed-url)]
       [:updated (format-date (:published-at (first posts)))]
       [:link {:rel "self" :href feed-url :type "application/atom+xml"}]
       [:link {:href (:url site)}]
       (for [post (take 10 posts)
             :let [url (str (:url site) "/p/" (:slug post) "/")]]
         [:entry
          [:title {:type "html"} (:title post)]
          [:id (url-encode url)]
          [:updated (format-date (:published-at post))]
          [:content {:type "html"} (:html post)]
          [:link {:href url}]
          [:author
           [:name (:author-name site)]
           [:uri (:author-url site)]]])]))

(defn user [{:keys [biff/db path-params]}]
  (let [messages (->> (q db
                         '{:find [(pull message [*])
                                  (pull guild [*])]
                           :in [user]
                           :where [[message :discord.message/author_id user]
                                   [message :discord.message/channel_id thread_id]
                                   [thread :discord.thread/id thread_id]
                                   [thread :discord.thread/guild_id guild_id]
                                   [guild :discord.guild/id guild_id]]}
                         (:id path-params))
                      (map (fn [[message guild]]
                             (assoc message :discord.message/guild guild)))
                      (sort-by :discord.message/timestamp #(compare %2 %1)))
        author (-> messages first :discord.message/author)
        guild->invite (into {} (q db
                                  '{:find [guild (pull invite [*])]
                                    :in [[guild ...]]
                                    :where [[invite :discord.invite/guild_id guild]]}
                                  (map (comp :discord.guild/id :discord.message/guild) messages)))]
    (ui/page
     {:base/title (str "@" (:username author))}
     [:div
      [:a.link {:href "/"} "Home"]
      " > "
      "@" (:username author)]
     #_[:.h-6]
     #_[:div "Subscribe: "
        [:a.link {:href "#"} "RSS"]
        ui/interpunct
        [:a.link {:href "#"} "Email"]]
     [:.h-6]
     (biff/join
      (list
       [:.h-6]
       [:hr]
       [:.h-6])
      (for [{:discord.message/keys [author content timestamp id channel_id author_id guild]} messages
            :let [invite (guild->invite (:discord.guild/id guild))]]
        [:div {:id id}
         [:div [:a.font-bold.hover:underline
                {:href (str "/user/" author_id)}
                "@" (:username author)]
          " " [:a.text-gray-600.hover:underline {:href (str "#" id)}
               (-> timestamp
                   biff/parse-date
                   (biff/format-date "d MMM yyyy, hh:mm a z"))]]
         [:.h-1]
         [:.whitespace-pre-wrap content]
         [:.h-3]
         [:.text-sm.leading-none.text-gray-600 (:discord.guild/name guild)]
         [:div
          [:a.text-sm.link {:href (str "https://discord.com/channels/"
                                       (:discord.guild/id guild) "/"
                                       channel_id "/"
                                       id)}
           "View on Discord"]
          (when invite
            (list
             ui/interpunct
             [:a.text-sm.link {:href (str "https://discord.gg/" (:discord.invite/code invite))}
              "Join this server"]))]])))))

(defn thread [{:keys [biff/db path-params]}]
  (let [{:discord.thread/keys [guild_id parent_id id]
         thread-name :discord.thread/name
         :as thread} (xt/entity db (keyword "discord.thread.id" (:id path-params)))
        guild (xt/entity db (keyword "discord.guild.id" guild_id))
        channel (xt/entity db (keyword "discord.channel.id" parent_id))
        messages (sort-by
                  :discord.message/timestamp
                  (q db
                     '{:find (pull message [*])
                       :in [thread]
                       :where [[message :discord.message/channel_id thread]]}
                     id))
        invite (first
                (q db
                   '{:find (pull invite [*])
                     :in [guild]
                     :where [[invite :discord.invite/guild_id guild]]}
                   guild_id))]
    (ui/page
     {:base/title thread-name}
     [:div
      [:a.link {:href "/"} "Home"]
      " > "
      [:a.link {:href (str "/server/" (:discord.guild/id guild))}
       (:discord.guild/name guild)]
      " > "
      [:a.link {:href (str "/channel/" (:discord.channel/id channel))}
       (:discord.channel/name channel)]
      " > "
      thread-name]
     [:.h-6]
     (biff/join
      (list
       [:.h-6]
       [:hr]
       [:.h-6])
      (for [{:discord.message/keys [author content timestamp id channel_id author_id]} messages]
        [:div {:id id}
         [:div [:a.font-bold.hover:underline
                {:href (str "/user/" author_id)}
                "@" (:username author)]
          " " [:a.text-gray-600.hover:underline {:href (str "#" id)}
               (-> timestamp
                   biff/parse-date
                   (biff/format-date "d MMM yyyy, hh:mm a z" ))]]
         [:.h-1]
         [:.whitespace-pre-wrap content]
         [:.h-1]
         [:div
          [:a.text-sm.link {:href (str "https://discord.com/channels/"
                                       (:discord.guild/id guild) "/"
                                       channel_id "/"
                                       id)}
           "View on Discord"]
          (when invite
            (list
             ui/interpunct
             [:a.text-sm.link {:href (str "https://discord.gg/" (:discord.invite/code invite))}
              "Join this server"]))]])))))

(defn channel [{:keys [biff/db path-params]}]
  (let [{:discord.channel/keys [guild_id id]
         channel-name :discord.channel/name
         :as channel} (xt/entity db (keyword "discord.channel.id" (:id path-params)))
        guild (xt/entity db (keyword "discord.guild.id" guild_id))
        threads (->> (q db
                        '{:find (pull thread [*])
                          :in [channel]
                          :where [[thread :discord.thread/parent_id channel]]}
                        id)
                     (sort-by :discord.thread/last_message_id #(compare %2 %1)))]
    (ui/page
     {:base/title channel-name}
     [:div
      [:a.link {:href "/"} "Home"]
      " > "
      [:a.link {:href (str "/server/" (:discord.guild/id guild))}
       (:discord.guild/name guild)]
      " > "
      channel-name]
     [:.h-6]
     [:ul
      (for [{:discord.thread/keys [name id]} threads]
        [:li [:a.link {:href (str "/thread/" id)} name]])])))

(defn guild [{:keys [biff/db path-params]}]
  (let [guild (xt/entity db (keyword "discord.guild.id" (:id path-params)))
        channels (sort-by
                  :discord.channel/position
                  (q db
                     '{:find (pull channel [*])
                       :in [guild]
                       :where [[channel :discord.channel/guild_id guild]]}
                     (:discord.guild/id guild)))]
    (ui/page
     {:base/title (:discord.guild/name guild)}
     [:div
      [:a.link {:href "/"} "Home"]
      " > "
      (:discord.guild/name guild)]
     [:.h-6]
     [:ul
      (for [{:discord.channel/keys [id name topic]} channels]
        [:li [:a.link {:href (str "/channel/" id)} name]
         (when (some-> topic str/trim not-empty)
           ": ")
         topic])])))

(defn home [{:keys [biff/db discord/invite-url]}]
  (let [guilds (sort-by
                :discord.guild/id
                (q db
                   '{:find (pull guild [*])
                     :where [[guild :discord.guild/id]]}))]
    (ui/page
     {}
     [:h1.text-2xl "Discord Forum Publisher"]
     [:p "By " [:a.link {:href "https://tfos.co"} "Jacob O'Bryant"]]
     [:p "This website hosts public copies of Discord forum channels. "
      "You can add your own Discord server by installing the "
      [:a.link {:href invite-url}
       "Forum Publisher bot"]
      ". " [:strong "Important:"] " If you have a non-expiring invite link, it will be published too."]
     [:p "Non-forum channels will not be published. Forum channels are synced every 30 minutes. "
      "To remove your server from this website, remove the Forum Publisher bot from your server."]
     [:p [:a.link {:href "https://github.com/jacobobryant/discord-forum-publisher"}
          "Source code"]
      ui/interpunct
      [:a.link {:href "https://github.com/jacobobryant/discord-forum-publisher/issues"}
       "Issues"]
      ui/interpunct
      [:a.link {:href "https://discord.tfos.co/thread/1034195503849689148"}
       "Rationale"]]
     [:.h-6]
     [:hr]
     [:.h-6]
     [:p "Servers:"]
     [:ul
      (for [{:discord.guild/keys [name id]} guilds]
        [:li [:a.link {:href (str "/server/" id)}
              name]])])))

(def features
  {:routes [["/" {:get home}]
            ["/server/:id" {:get guild}]
            ["/channel/:id" {:get channel}]
            ["/thread/:id" {:get thread}]
            ["/user/:id" {:get user}]]})
