(ns co.tfos.discord.ui
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

(def interpunct " · ")

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [opts & body]
  (apply
   biff/base-html
   (-> (merge #:base{:title "Discord Forum Publisher"
                     :lang "en-US"}
              opts)
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]]
                                    head))))
   body))

(defn page [opts & body]
  (base
   opts
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    body]
   [:.h-6]))
