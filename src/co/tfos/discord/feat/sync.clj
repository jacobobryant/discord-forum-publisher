(ns co.tfos.discord.feat.sync
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.)))

(defn api [{:keys [discord/token]} method url & [options]]
  (Thread/sleep 100)
  (http/request (-> {:as :json
                     :url (str "https://discord.com/api" url)
                     :method method}
                    (merge options)
                    (assoc-in [:headers "Authorization"] (str "Bot " token)))))

(defn sync-discord! [{:keys [biff/db discord/token] :as sys}]
  (log/info "start syncing")
  (let [guilds (-> (api sys :get "/users/@me/guilds")
                   :body)
        channels (for [guild guilds
                       channel (:body (api sys :get (str "/guilds/" (:id guild) "/channels")))]
                   channel)
        invites (for [channels (partition-by :guild_id channels)
                      :let [invite (->> channels
                                        (sort-by :position)
                                        (mapcat (fn [{:keys [id]}]
                                                  (:body (api sys :get (str "/channels/" id "/invites")))))
                                        (remove :expires_at)
                                        first)]
                      :when invite]
                  (assoc invite
                         :guild_id (get-in invite [:guild :id])
                         :id (:code invite)))
        forum-channels (for [channel channels
                             :when (= 15 (:type channel))]
                         channel)
        forum-ids (set (map :id forum-channels))
        threads (for [guild guilds
                      thread (-> (api sys :get (str "/guilds/" (:id guild) "/threads/active"))
                                 :body
                                 :threads)
                      :when (contains? forum-ids (:parent_id thread))]
                  thread)
        messages (for [thread threads
                       message (:body (api sys :get (str "/channels/" (:id thread) "/messages")))]
                   (assoc message :author_id (get-in message [:author :id])))
        tx (for [[docs prefix type] [[guilds 'discord.guild :discord/guild]
                                     [forum-channels 'discord.channel :discord/channel]
                                     [threads 'discord.thread :discord/thread]
                                     [messages 'discord.message :discord/message]
                                     [invites 'discord.invite :discord/invite]]
                 doc docs]
             (assoc (biff/select-ns-as doc nil prefix)
                    :xt/id (keyword (str prefix ".id") (:id doc))
                    :db/doc-type type))
        all-ids (set (map :xt/id tx))
        rm-tx (for [doc-id (q db
                              '{:find doc
                                :where [[doc :xt/id]]})
                    :when (and (str/starts-with? (namespace doc-id) "discord.")
                               (not (all-ids doc-id)))]
                {:db/op :delete
                 :xt/id doc-id})]
    (biff/submit-tx sys (concat tx rm-tx))
    (log/info "done syncing")))

(def features
  {:tasks [{:task #'sync-discord!
            :schedule #(every-n-minutes 30)}]})
