;; Copyright © 2020 Dynamic Object Language Labs Inc.
;; DISTRIBUTION STATEMENT C: U.S. Government agencies and their contractors.
;; Other requests shall be referred to DARPA’s Public Release Center via email at prc@darpa.mil.

(ns imcw.import-minecraft-world
  "RITA Import Minecraft World."
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
            [clojure.java.io :as io])
  (:gen-class)) ;; required for uberjar

#_(in-ns 'rita.state-estimation.import-minecraft-world)

;;; Removes air, converts coordinates into numbers and calculates the minimum and maximum
;;; of each dimension
(defn import-world
  [json-map dontdehydrate adj]
  (let [[ax ay az] adj
        pairs (into [] json-map)]
    (loop [data (filter (fn [item] (or dontdehydrate (not (= (second item) "air")))) pairs)
           maxx -10000 minx 100000 maxy -10000 miny 100000 maxz -10000 minz 100000
           retained {}]
      (let [[key val] (first data)]
        (if (not (empty? data))
          (let [coordinates (into [] (map read-string (clojure.string/split key #",")))
                [rx rz ry] coordinates
                x (+ rx ax)
                y (+ ry ay)
                z (+ rz az)]
            (recur (rest data)
                   (Math/max maxx x) (Math/min minx x)
                   (Math/max maxy y) (Math/min miny y)
                   (Math/max maxz z) (Math/min minz z)
                   (conj retained {[x z y] (keyword val)})))
          {:minx minx :maxx maxx :miny miny :maxy maxy :minz minz :maxz maxz :data retained})))))

(defn export-world-to-file
  [world objects fname]
  (let [data (:data world)
        json-data (conj objects
                        {:data (into []
                                     (map (fn [datum]
                                            (let [[[x y z] val] datum
                                                  ;;kw (str (str x) "," (str y) "," (str z))
                                                  kw [ x y z ]
                                                  valstr (name val)]
                                              [kw valstr]))
                                          data))})]
    (spit fname (json/write-str json-data))))

(defn import-world-from-file
  [fn  dontdehydrate & adj]
  (let [adjust (if (empty? adj) [0 0 0] (first adj))
        json (and fn (.exists (io/file fn)) (json/read-str (slurp fn)))]
    (if (not json)
      (do (println "File not found: " fn) (System/exit -1))
      (import-world json dontdehydrate adjust))))

(defn world-dimensions
  [world]
  [[(:minx world) (:maxx world)]
   [(:minz world) (:maxz world)]
   [(:miny world) (:maxy world)]])

(defn world-size
  [world]
  [(+ 1 (- (:maxx world) (:minx world)))
   (+ 1 (- (:maxz world) (:minz world)))
   (+ 1 (- (:maxy world) (:miny world)))])

(defn world-object-types
  [world]
  (set (map (fn [[dims val]] val) (:data world))))

;;; The order of coordinates is [x z y] we organize storage as [x y z]
;;; Sorting puts things ordered by x and y, which is useful for grouping.
(defn compute-index
  [world objcoords]
  (let [[xsize zsize ysize] (world-size world)
        mins (map first (world-dimensions world))
        adjcoords (map - objcoords mins)
        [x z y] adjcoords]
    (+ z (* y zsize) (* x zsize ysize))))

(defn compute-mda-index
  [world objcoords]
  (let [[xsize zsize ysize] (world-size world)
        mins (map first (world-dimensions world))
        adjcoords (map - objcoords mins)]
    ;;(println "size= [" xsize zsize ysize "] adjcoords=" adjcoords)
    adjcoords))

(defn get-world-objects-of-type
  [world type]
  (let [cf (fn [x y] (> (compute-index world x) (compute-index world y)))]
    (sort-by first cf (filter (fn [item] (= (second item) type)) (:data world)))))

(defn distance-between-voxels
  [world v0 v1]
  ;(println "v0=" v0 "v1=" v1)
  (let [[x0 z0 y0] (first v0)
        [x1 z1 y1] (first v1)
        dist (Math/sqrt (+ (* (- x1 x0) (- x1 x0))
                           (* (- z1 z0) (- z1 z0))
                           (* (- y1 y0) (- y1 y0))))]
    dist))

(defn door-finder-aux
  [world voxels]
  (loop [doorparts [(first voxels)]
         unusedparts (rest voxels)]
    (if (or (empty? unusedparts)
            (>= (distance-between-voxels world (last doorparts) (first unusedparts)) 2))
      [(map first doorparts) unusedparts]
      (recur (conj doorparts (first unusedparts)) (rest unusedparts)))))

(defn door-finder
  [world dtype]
  (let [doorvoxels (get-world-objects-of-type world dtype)]
    (if (not (empty? doorvoxels))
      (loop [parts []
             unused doorvoxels]
        (if (not (empty? unused))
          (let [[part remaining] (door-finder-aux world unused)]
            (recur (conj parts part) remaining))
          parts)))))

(def type-char-map
  {
   :anvil \a
   :bookshelf \B
   :cauldron \O
   :chest \c
   :clay \C
   :cobblestone \4
   :cobblestone_wall \W
   :crafting_table \t
   :dispenser \d
   :dropper \5
   :end_portal_frame \E
   :fire \F
   :flower_pot \P
   :furnace \f
   :glass_pane \|
   :gravel \g
   :heavy_weighted_pressure_plate \H
   :hopper \7
   :iron_bars \I
   :ladder \L
   :lever \l
   :lit_redstone_lamp \8
   :monster_egg \e
   :nether_brick \b
   :piston_head \h
   :redstone_torch \r
   :redstone_wire \W
   :snow \o
   :stained_hardened_clay \S
   :sticky_piston \k
   :stone_button \3
   :stone_slab \s
   :tripwire_hook \6
   :unlit_redstone_torch \u
   :unpowered_repeater \2
   :wall_sign \w
   :wooden_button \b
   :wool \9
   :bedrock \&
   :fence \#
   :glass \@
   :bone_block \X
   :brick_block \X
   :diamond_block \X
   :iron_block \i
   :quartz_block \Q
   :redstone_block \R
   :command_block \~
   :gold_block \+
   :prismarine \=
   :standing_sign \%

   :barrier \*
   :brewing_stand \*
   :cake \*
   :double_stone_slab \*
   :hardened_clay \S
   :leaves \*
   :nether_brick_fence \*
   :planks \*


   :nether_brick_stairs \N
   :oak_stairs \O
   :quartz_stairs \q
   :birch_stairs \s
   :brick_stairs \s
   :spruce_stairs \s

   :stained_glass \|
   :stained_glass_pane \|
   :stone \*
   :wall_banner \*
   :water \|

   :wooden_door \D
   :acacia_door \D
   :dark_oak_door \D
   :iron_door \D
   :spruce_fence_gate \D
   :fence_gate \[
   })

#_(def ^:dynamic *objects-of-interest* {})

#_(defn find-objects-of-interest
  [world]
  (let [found-ooi (into {} (map (fn [[kw nme]]
                                  {kw (get-world-objects-of-type world kw)})
                               objects-of-interest))]
    (def ^:dynamic *objects-of-interest* found-ooi)))

#_(defn print-object-of-interest-as-pamela-constructors[]
  (doseq [[kw soo] *objects-of-interest*]
    (let [pclassname (str "Minecraft-object-" (name kw))]
      (println (str "(defpclass " pclassname " [iblx ibly iblz itrx itry itrz vname] :inherit [RectangularVolume])"))))
  (println)
  (println "(defpclass Minecraft-objects []")
  (println "  :fields {")
  (doseq [[kw soo] *objects-of-interest*]
    (let [constructor-name (str "Minecraft-object-" (name kw))]
      (println "           ;; Minecraft objects of type " kw)
      (doseq [[[x z y] otn] soo]
        (println (str "           " (name (gensym (name kw))))
                 (str "(" constructor-name) x y z x y z (str "\"" (name kw) "\")")))))
  (println "          })"))

(defn make-minecraft-array
  [world]
  (let [dims (world-size world)
        array (apply make-array Character/TYPE dims)]
    (dotimes [x (nth dims 0)]
      (dotimes [z (nth dims 1)]
        (dotimes [y (nth dims 2)]
          (aset-char array x z y \ ))))
    (doseq [voxel (:data world)]
      (let [[x z y] (compute-mda-index world (first voxel))
            val (get type-char-map (second voxel) \?)]
        (if (= val \?) (println "No entry in type-char-map for:" (second voxel)))
        (aset-char array x z y val)))
    array))

(defn world-set
  [world mca val & coords]
  (let [[cx cz cy] (compute-mda-index world coords)
        dims (world-size world)
        val (get type-char-map val \?)]
    (aset-char mca cx cz cy val)))

(defn generate-data
  [val x y z]
  (println (str "\"" (str (- x -35)) "," (str y) "," (str (- z -9)) "\"")
           ":"
           (str "\"" (name val) "\",")))

(defn print-world
  [world mca]
  (let [[sx sz sy] (world-size world)
        [dx dz dy] (world-dimensions world)]
    (println "There are" sz "levels from" (first dz) "to" (second dz)
             "startx (left)=" (first dx) "starty (bottom)=" (first dy))
    (println)
    (dotimes [level sz]
      (println "Level " (+ level (first dz)))
      (println)
      (dotimes [yline sy]
        (let [y yline] ;(- (- sy 1) yline)]
          (dotimes [x sx]
            ;;(print \ )
            (print (aget mca x level y)))
          (println))))))

(defn print-world-wide
  [world mca]
  (let [[sx sz sy] (world-size world)
        [dx dz dy] (world-dimensions world)]
    (println "There are" sz "levels from" (first dz) "to" (second dz))
    (println "startx (left)=" (first dx) "to" (second dx) "starty (bottom)=" (first dy) "to" (second dy))

    (println)
    (dotimes [level sz]
      (println "Level " (+ level (first dz)))
      (println)
      (dotimes [yline sy]
        (let [y yline] ;(- (- sy 1) yline)]
          (dotimes [x sx]
            (if (= \S (aget mca x level y))
              (do (print \[)
                  ;;(print (aget mca x level y))
                  (print \]))
              (do (print \ ) (print \ ))))
          (println))))))

(defn print-doors-as-clojure
  [doorlist]
  (doseq [adoor doorlist]
    (case  (count adoor)
      2
      (let [[[x1 y1 z1][x2 y2 z2]] adoor]
        (println (gensym "door") "(Door"
                 (min x1 x2)
                 (min z1 z2)
                 (min y1 y2)
                 (max x1 x2)
                 (max z1 z2)
                 (max y1 y2) "\"name of door\")"))

      4
      (let [[[x1 y1 z1][x2 y2 z2][x3 y3 z3][x4 y4 z4]] adoor]
        (println (gensym "door") "(DoubleDoor"
                 (min x1 x2 x3 x4)
                 (min z1 z2 z3 z4)
                 (min y1 y2 y3 y4)
                 (max x1 x2 x3 x4)
                 (max z1 z2 z3 z4)
                 (max y1 y2 y3 y4) "\"name of double door\")"))

      (println "Doors should have an even number of coordinates: " adoor))))

;;; Fin

;;; (def world (import-world-from-file  "/Users/paulr/checkouts/bitbucket/asist_rita/Code/data/blocks_in_building_2.json")) ;[-35 -9 0]

;;; (def world (import-world-from-file  "/Users/paulr/checkouts/bitbucket/asist_rita/Code/data/blocks_in_building_falcon_m.json")) ;[-35 -9 0]
;;; (def w-dimensions (world-dimensions world))
;;; (def w-size (world-size world))
;;; (def w-types (world-object-types world))
;;; (pprint w-types)
;;; (def w-doors (get-world-objects-of-type world :wooden_door))
;;; (def a-doors (get-world-objects-of-type world :acacia_door))
;;; (def d-doors (get-world-objects-of-type world :dark_oak_door))
;;; (def i-doors (get-world-objects-of-type world :iron_door))
;;; (def s-gates (get-world-objects-of-type world :spruce_fence_gate))
;;; (def f-gates (get-world-objects-of-type world :fence_gate))
;;; (def all-doors (concat w-doors a-doors d-doors i-doors))
;;; (pprint all-doors)
;;; (def wdoors (door-finder world :wooden_door))
;;; (def alldoors (apply concat (map (fn [dtype] (door-finder world dtype)) [:wooden_door :acacia_door :dark_oak_door :iron_door])))
;;; (pprint alldoors)
;;; (print-doors-as-clojure alldoors)
;;; (def mca (make-minecraft-array world))
;;; (print-world world mca)
;;; (find-objects-of-interest world)
;;; (print-object-of-interest-as-pamela-constructors)
;;; (print-world-wide world mca)
;;; (export-world-to-file world "/Users/paulr/checkouts/bitbucket/asist_rita/Code/data/falcon-exported-world.json")
;;; (def world (import-world-from-file  "/Users/paulr/checkouts/bitbucket/asist_rita/Code/data/exported-world.json"))

(defn xlate
  [x1 y1 z1 x2 y2 z2]
  (println (+ x1 35) (+ y1 9) (+ z1 24) (+ x2 35) (+ y2 9) (+ z2 24)))

;;; (xlate -2174 155 30 -2174 155 30)
