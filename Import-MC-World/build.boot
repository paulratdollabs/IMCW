;; Copyright © 2016 Dynamic Object Language Labs Inc.
;;
;; This software is licensed under the terms of the
;; Apache License, Version 2.0 which can be found in
;; the file LICENSE at the root of this distribution.

(def project 'dollabs/imcw)
(def version "0.2.0")
(def description "Import Minecraft world")
(def project-url "https://TBD/dollabs/imcw")
(def main 'imcw.cli)

(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [avenir "0.2.1"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/tools.cli "0.3.5"]
                 [environ "1.1.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.taoensso/timbre "4.7.4"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.novemberain/langohr "3.6.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.csv "0.1.4"]])

(require
  '[clojure.string :as string]
  '[boot.util :as util])

(task-options!
  pom {:project project
       :version version
       :description description
       :url project-url
       :scm {:url project-url}
       :license     {"Apache-2.0" "http://opensource.org/licenses/Apache-2.0"}}
  aot {:namespace #{main}}
  jar {:main main})

(deftask clj-dev
  "Clojure REPL for CIDER"
  []
  (comp
    (cider)
    (repl :server true)
    (wait)))

(deftask cider-boot
  "Cider boot params task"
  []
  (clj-dev))

(deftask build-jar
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp
      (sift :include #{#"~$"} :invert true) ;; don't include emacs backups
      (aot)
      (uber)
      (jar :file (str (name project) ".jar"))
      (target :dir dir))))

(deftask cli
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  (require [main :as 'app])
  (let [argv (if (pos? (count args))
               (clojure.string/split (first args) #" ")
               '())]
    (with-post-wrap [_]
      (apply (resolve 'app/-main) argv))))

(deftask run
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  ;; (reset! util/*verbosity* 0) ;; quiet output
  (cli :args args))

(deftask debug
  "Start in the repl."
  []
  (require [main :as 'app])
  (repl :server false))
