(ns superstack.main
  (:require [clojure.data.json :as json]
            [clojure.instant :as instant]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [selmer.parser :as sparser]
            [selmer.util :as sutil]))

;; ========== SETUP ============================================================
;; Workaround to ignore tags not present in a selmer substitution map.
(sutil/set-missing-value-formatter!
 (fn [tag _]
   (str "{{" (:tag-value tag) "}}")))

(def ^:private re-slug
  (partial re-find #"[^/]+$"))

(def ^:private start-of-line
  (format "\033[1A\033[2K"))

(def ^:private additional-assets
  ["assets/posts.css"
   "assets/index.css"
   "assets/sun.svg"
   "assets/moon.svg"])

(defn- read-data
  [location]
  (-> location
      slurp
      (json/read-str :key-fn keyword)))

;; ========== PREPARATION ======================================================
(defn- parse-data-from-file
  [filename]
  (let [add-counter (fn [n m]
                      (assoc m :number (format "%04d" n)))
        add-slug (fn [m]
                   (assoc m :slug (str (:number m)
                                       "-"
                                       (s/replace (re-slug (:url m)) #"\?.*$" "")
                                       ".html")))
        escape-codes (fn [m]
                       (update m :text_html #(s/replace % "%" "&#37;")))]
    (->> filename
         read-data
         (mapv escape-codes)
         (mapv add-counter (rest (range)))
         (mapv add-slug))))

(defn- parse-post
  [data]
  (let [file (slurp "templates/substack.html")
        post-data (select-keys data [:text_html :title :subtitle :date :url])
        intermediate {:first? "{% if first? %} hidden{% endif %}"
                      :last? "{% if last? %} hidden{% endif %}"}
        substitution (-> post-data
                         (update :date instant/read-instant-date)
                         (merge intermediate))]
    (sutil/without-escaping
     (sparser/render file substitution))))

;; ========== OUTPUT ===========================================================
(defn- spit-first-pass!
  "Spit half-formatted individual pages for every article"
  [{:keys [data target]}]
  (println "| Formatting first pass" \newline)
  (doseq
   [post data
    :let [slug (:slug post)
          html-str (parse-post post)]]
    (println start-of-line "" slug)
    (spit (str target "posts/" slug) html-str)))

(defn- spit-second-pass!
  "Populate previous and next links between a list"
  [{:keys [data target]}]
  (println "| Formatting second pass" \newline)
  (let [slugs (mapv :slug data)]
    (loop [[active & remaining] slugs]
      (if (< 0 (count remaining))
        (let [prv-name (str target "posts/" active)
              nxt-name (str target "posts/" (first remaining))
              [prv nxt] (mapv slurp [prv-name nxt-name])]
          (println start-of-line "" active)
          (spit prv-name
                (sutil/without-escaping
                 (sparser/render
                  prv
                  {:next (first remaining)
                   :first? (= (count slugs) (inc (count remaining)))})))
          (spit nxt-name
                (sutil/without-escaping
                 (sparser/render
                  nxt
                  {:prev active
                   :last? (= 1 (count remaining))})))
          (recur remaining))
        (println start-of-line \space (str target "posts/" active))))))

(defn- create-index!
  [{:keys [data target]}]
  (println "| Creating index file")
  (let [template (sparser/render
                  (slurp "templates/substack-index.html")
                  {:items data})]
    (spit (str target "index.html") template)))

(defn- copy-assets!
  "Copy files given by a vec of strs"
  [target files]
  (println "| Copying additional files")
  (doseq [file files
          :let [slug (re-slug file)]]
    (println \space slug)
    (io/copy
     (io/file (str file))
     (io/file (str target slug)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn spit-all!
  [args]
  (let [args (update-vals args str)]
    (if-not (.exists (io/file (:src-data args)))
      (println "  DNE:" (:src-data args) \newline
               " Please ensure a JSON file exists at the"
               "default location or path specified with :src-data.")
      (let [{:keys [src-data blog-name output-dir]} args
            scrub #(-> %
                       (s/replace #"[\s\/]" "-")
                       (s/replace #"[^-\w]" ""))
            inputs {:data (parse-data-from-file src-data)
                    :target (str (scrub output-dir) "/" (scrub blog-name) "/")}]
        (io/make-parents output-dir blog-name "posts" "tmp")
        (spit-first-pass! inputs)
        (spit-second-pass! inputs)
        (create-index! inputs)
        (copy-assets! (:target inputs) additional-assets)
        (println "Complete" (char 0x3020))))))
