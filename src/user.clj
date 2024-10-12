(ns user
  (:require [clj-rss.core :as rss]
            [clojure.data.xml :as xml]
            [net.cgrand.enlive-html :as enlive-html]
            [clojure.string :as str])
  (:import (java.net URL)))

(defn relative?
  [link]
  (->> link
       (re-find #"^https?://")
       boolean
       not))

(defn strip-cache-buster
  [link]
  (str/replace link #"t=\d+" ""))

(defn essays []
  (map (fn [{:keys [attrs content]}]
         (let [title (first content)
               link  (:href attrs)]
           {:title title
            :link  (cond->> link
                     true             strip-cache-buster
                     (relative? link) (str "https://paulgraham.com/"))}))
       (-> "https://paulgraham.com/articles.html"
           URL.
           enlive-html/html-resource
           (enlive-html/select [:td (enlive-html/nth-child 2) :a]))))

(defn rss [essays]
  (->> essays
       (rss/channel {:title       "Paul Graham: Essays"
                     :link        "http://www.paulgraham.com/"
                     :description "Scraped feed provided by https://github.com/filipesilva/paulgraham-rss"})
       xml/indent-str))

(defn -main []
  (->> (essays)
       reverse
       rss
       (spit "./_site/feed.rss")))

(comment

  (relative? "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl2.txt?t=1727692616&amp")
  (relative? "avg.html")

  (strip-cache-buster "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl2.txt?t=1727692616&amp")

  (-main)

  ;;
  )
