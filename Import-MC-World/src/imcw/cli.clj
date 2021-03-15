(ns imcw.cli
  "Import Minecraft World Tool"
  (:require [clojure.tools.cli :as cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [clojure.data.codec.base64 :as base64]
            [clojure.string :as string]
            [clojure.pprint :as pp :refer [pprint]]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [clojure.data.xml :as xml]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.repl :refer [pst]]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [imcw.import-minecraft-world :refer :all]
            )
  (:gen-class))

(def repl true)

(def cli-options [
                  ["-o" "--output file"    "output"                               :default "mcworld.json"]
                  ["-i" "--import file"    "Import from extracted json"           :default false]
                  ["-d" "--dont-dehydrate" "Don't remove air"                     :default false]
                  ["-m" "--map"            "Print the map"                        :default false]
                  ["-l" "--levers"         "find all levers (switches)"           :default false]
                  ["-v" "--victims"        "find all victims"                     :default false]
                  ["-h" "--help"]]
                  )

(defn testaction
  ""
  [& args]
  (.write *out* (format "%ntest %n" args)))

(defn importmcw
  ""
  [& args]
  (.write *out* (format "%nGenerate the minecraft-world: %s%n" args)))

(def #^{:added "0.1.0"}
  actions
  "Valid cvg command line actions"
  {"import" (importmcw)
   })

(defn usage
  "Print imcw command line help."
  {:added "0.1.0"}
  [options-summary]
  (->> (for [a (sort (keys actions))]
         (str "  " a "\t" (:doc (meta (get actions a)))))
    (concat [""
             "imcw"
             ""
             "Usage: imcw [options] action"
             ""
             "Options:"
             options-summary
             ""
             "Actions:"])
    (string/join \newline)))

(defn change-extn
  [astring newext]
  (let [index (string/last-index-of astring ".")]
    (if index (str (subs astring 0 index) "." newext) (str astring "." newext))))

(defn strip-extn
  [astring]
  (let [index (string/last-index-of astring ".")]
    (if index (subs astring 0 index) astring)))

(defn imcw-main
  "import minecraft world main"
  [& args]
  (let [parsed (cli/parse-opts args cli-options)
        {:keys [options arguments error summary]} parsed
        {:keys [help version verbose import] } options

        pmap  (get-in parsed [:options :map])
        input (get-in parsed [:options :import])
        outf  (get-in parsed [:options :output])
        vict  (get-in parsed [:options :victims])
        levr  (get-in parsed [:options :levers])
        dehy  (get-in parsed [:options :dehydrate])]

    (when help
      (println (usage (:summary parsed)))
      (System/exit 0))

    (cond (>= (count arguments) 1)
          (case (keyword (first arguments))
            :import
            (let [world (import-world-from-file input dehy)
                  w-dimensions (world-dimensions world)
                  w-size (world-size world)
                  w-types (world-object-types world)
                  levers (if levr {:levers (get-world-objects-of-type world :lever)})
                  victims (if vict {:victims (apply concat (map (fn [vtype] (get-world-objects-of-type world vtype))
                                                                [:prismarine :gold_block]))})
                  w-doors (get-world-objects-of-type world :wooden_door)
                  a-doors (get-world-objects-of-type world :acacia_door)
                  d-doors (get-world-objects-of-type world :dark_oak_door)
                  i-doors (get-world-objects-of-type world :iron_door)
                  s-doors (get-world-objects-of-type world :spruce_door) ; New in Saturn
                  s-gates (get-world-objects-of-type world :spruce_fence_gate)
                  f-gates (get-world-objects-of-type world :fence_gate)
                  b-gates (get-world-objects-of-type world :birch_fence_gate) ; New in Saturn
                  all-doors-and-gates (concat w-doors a-doors d-doors i-doors s-doors s-gates f-gates b-gates)
                  alldoors {:doors (apply concat (map (fn [dtype] (single-door-finder world dtype))
                                              [:wooden_door :acacia_door :dark_oak_door :iron_door :spruce_door]))}]
              (if pmap
                (let [mca (make-minecraft-array world)]
                  (print-world world mca)))
              (export-world-to-file world (conj (conj alldoors levers) victims) outf))

            (println "Unknown command: " (first arguments) "try: import"))

          :else
          (do
            (println "No command specified, try import")
            (System/exit 0)))))

(defn -main
  "cvg"
  {:added "0.1.0"}
  [& args]
  (apply imcw-main args))

;;; Fin
