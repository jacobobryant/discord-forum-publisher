(ns co.tfos.discord.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; As of writing this, calling (biff/refresh) with Conjure causes stdout to
  ;; start going to Vim. fix-print makes sure stdout keeps going to the
  ;; terminal. It may not be necessary in your editor.
  (biff/fix-print (biff/refresh))

  (time (co.tfos.discord.feat.sync/sync-discord! (get-sys)))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    )

  (sort (keys @biff/system))
  )
