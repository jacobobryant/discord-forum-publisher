(ns co.tfos.discord.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]
            [com.biffweb :refer [doc-schema] :rename {doc-schema doc}]))

(def schema
  {:discord/guild   (doc {:required [[:xt/id :keyword]]
                          :wildcards {'discord.guild any?}})
   :discord/channel (doc {:required [[:xt/id :keyword]]
                          :wildcards {'discord.channel any?}})
   :discord/thread  (doc {:required [[:xt/id :keyword]]
                          :wildcards {'discord.thread any?}})
   :discord/message (doc {:required [[:xt/id :keyword]]
                          :wildcards {'discord.message any?}})
   :discord/invite  (doc {:required [[:xt/id :keyword]]
                          :wildcards {'discord.invite any?}})})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
