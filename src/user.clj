(ns user
  (:require [clj-rss.core :as rss]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [java-time.api :as jt]
            [net.cgrand.enlive-html :as enlive-html])
  (:import (java.net URL)))

(defn html
  [url]
  (-> url URL. enlive-html/html-resource))

;; faster testing
(defonce memo-html (memoize html))

(defn relative?
  [link]
  (->> link
       (re-find #"^https?://")
       boolean
       not))

(defn strip-cache-buster
  [link]
  (str/replace link #"t=\d+" ""))

(def date-re
  #"(?:January|February|March|April|May|June|July|August|September|October|November|December) (?:\d{4})")

(defn month-year-to-rfc-1123
  [s]
  (try
    (-> (jt/local-date "dd LLLL yyyy" (str "01 " s))
        .atStartOfDay
        (jt/zoned-date-time "GMT")
        jt/instant)
    (catch Exception _)))

(defn pubdate
  [link]
  (when (str/starts-with? link "https://paulgraham.com/")
    (if (= link "https://paulgraham.com/foundervisa.html")
      ;; this one is a bit weird because the date is on the parent td instead,
      ;; and I don't know how to select for it.
      (month-year-to-rfc-1123 "April 2009")
      (some->> (enlive-html/select (memo-html link) [:td :td #{:font :p}])
               (mapcat :content)
               (filter string?)
               (keep #(re-seq date-re %))
               ;; first line that has dates
               ;; e.g. December 2019
               first
               ;; last date in that line
               ;; e.g. April 2001, rev. April 2003
               last
               month-year-to-rfc-1123))))

(defn fetch-essays []
  (->> (enlive-html/select (memo-html "https://paulgraham.com/articles.html")
                           [:td (enlive-html/nth-child 2) :a])
       (map (fn [{:keys [attrs content]}]
              (let [title (first content)
                    href  (:href attrs)
                    link  (cond->> href
                            true             strip-cache-buster
                            (relative? href) (str "https://paulgraham.com/"))]
                {:title title
                 :link  link})))
       ;; pull pub dates in parallel
       (pmap #(merge % (when-let [d (pubdate (:link %))]
                         {:pubDate d})))))

(defn rss [essays]
  (->> essays
       (rss/channel {:title       "Paul Graham: Essays"
                     :link        "http://www.paulgraham.com/"
                     :description "Scraped feed provided by https://github.com/filipesilva/paulgraham-rss"})
       xml/indent-str))

(def essays-without-date
  #{"https://paulgraham.com/fix.html"
    "https://paulgraham.com/noop.html"
    "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl1.txt?&"
    "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl2.txt?&"
    "https://paulgraham.com/progbot.html"})

(defn missing-pubdate
  ([] (missing-pubdate (fetch-essays)))
  ([essays']
   (->> essays'
        (filter #(-> % :pubDate nil?))
        (map :link)
        (remove essays-without-date))))

(defn assert-no-missing-pubdate
  [essays]
  (let [missing (->> essays
                     (filter #(-> % :pubDate nil?))
                     (map :link)
                     (remove essays-without-date))]
    (assert (empty? missing) (str "Missing pubdate in " (str/join " " missing))))
  essays)

(defn -main []
  (->> (fetch-essays)
       assert-no-missing-pubdate
       reverse
       rss
       (spit "./_site/feed.rss")))

(comment

  (relative? "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl2.txt?t=1727692616&amp")
  (relative? "avg.html")

  (strip-cache-buster "https://sep.turbifycdn.com/ty/cdn/paulgraham/acl2.txt?t=1727692616&amp")

  ;; body > table > tbody > tr > td:nth-child(3) > table:nth-child(4) > tbody > tr > td > font > p
  (pubdate "https://paulgraham.com/avg.html")
  ;; body > table > tbody > tr > td:nth-child(3) > table:nth-child(4) > tbody > tr > td > font
  (pubdate "https://paulgraham.com/kids.html")
  ;; body > table > tbody > tr > td:nth-child(3) > table > tbody > tr > td > font
  (pubdate "https://paulgraham.com/foundervisa.html")

  (fetch-essays)

  (missing-pubdate)

  (-main)

  ;;
  )
