(ns user
  (:require [clojure.data.xml :as xml]
            [clj-rss.core :as rss]
            [net.cgrand.enlive-html :as enlive-html])
  (:import (java.net URL)))

(defn essays []
  (map (fn [{:keys [attrs content]}]
         (let [title (first content)
               link  (:href attrs)]
           {:title title
            :link  (if (#{"Chapter 1 of Ansi Common Lisp"
                          "Chapter 2 of Ansi Common Lisp"}
                        title)
                     ;; These are not relative links
                     link
                     (str "https://paulgraham.com/" link))}))
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
  (-main))
