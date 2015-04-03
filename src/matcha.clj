(ns matcha
  (:refer-clojure
    :exclude
    [empty? every?
     = some not <= >= < >
     instance? string? map? seq? char? vector? nil? keyword? symbol?])
  (:require [clojure.string :as string]
            [clojure.core :as core]))
;; Matcher
;; Match
;;
;; is :: a -> Matcher a
;; allOf [Matcher a] -> Matcher a
;; runMatcher -> Matcher a -> a -> Match

;; a matcher is a map with three fields, each of whose values are functions:
;; {:match ;; a -> Bool
;;  :description :: String
;;  :describe-mismatch :: a -> String }
;;
;;  a match is a map with pass? and message, message will be a string
;;  if pass? if false
;;
;;  TODO: matchers list
;;  - has-nth
;;  - has-numerator
;;  - has denominator
;;  - decimal?
;;  - float?
;;  - rational?
;;  - isa?
;;  - coll?
;;  - list?
;;  - set?
;;  - fn?
;;  - re-matches
;; -- ** Matcher combinators
;; , is-not
;; , any-of
;; , on
;; , and-also
;; typed matchers:
;; -- ** Utility functions for writing your own matchers
;; , matcher-on
;; , match-list
(defn describe-list [call xs]
  (str "(" call " " (clojure.string/join " " xs) ")"))

(defn standard-describe-mismatch [x]
  (str "was " (pr-str x)))

(defn =
  "matches based on equality of the value given

  (matcha/run-match (matcha/= 1) 1) ; => passes
  (matcha/run-match (matcha/= 1) 2) ; => fails"
  [a]
  {:match (fn [b] (core/= a b))
   :description (describe-list "=" [a])
   :describe-mismatch standard-describe-mismatch})

(defn <=
  "matches based if the value given is greater-than or equal to

  (matcha/run-match (matcha/<= 1) 1) ; => passes
  (matcha/run-match (matcha/<= 1) 0) ; => fails"
  [a]
  {:match (fn [b] (core/<= a b))
   :description (describe-list "<=" [a])
   :describe-mismatch standard-describe-mismatch})

(defn <
  "matches based if the value given is greater-than or equal to

  (matcha/run-match (matcha/< 1) 2) ; => passes
  (matcha/run-match (matcha/< 1) 0) ; => fails"
  [a]
  {:match (fn [b] (core/< a b))
   :description (describe-list "<" [a])
   :describe-mismatch standard-describe-mismatch})

(defn >
  "matches based if the value given is greater-than or equal to

  (matcha/run-match (matcha/> 1) 2) ; => passes
  (matcha/run-match (matcha/> 1) 0) ; => fails"
  [a]
  {:match (fn [b] (core/> a b))
   :description (describe-list ">" [a])
   :describe-mismatch standard-describe-mismatch})

(defn >=
  "matches based if the value given is less-than or equal to

  (matcha/run-match (matcha/<= 1) 1) ; => passes
  (matcha/run-match (matcha/<= 1) 0) ; => fails"
  [a]
  {:match (fn [b] (core/>= a b))
   :description (describe-list ">=" [a])
   :describe-mismatch standard-describe-mismatch})

(def empty?
  "matches if the collection passed is empty

  (matcha/run-match matcha/empty? [1]) ; => passes
  (matcha/run-match matcha/empty? [])  ; => fails"
  {:match core/empty?
   :description "(empty?)"
   :describe-mismatch standard-describe-mismatch})

(defn format-message
  "turns the results of a failing match into a human readable error message,
   suitable for printing with clojure.core/print or clojure.core/println"
  [result]
  (str "\nExpected: " (:expected result)
       "\n     but: "
       (:was result)))

(defn all-of
  "matches if all of the matchers given pass:

  (matcha/run-match (matcha/all-of (matcha/= 1) (matcha/= 1)) 1) ; => passes
  (matcha/run-match (matcha/all-of (matcha/= 1) (matcha/= 1)) 2) ; => fails"
  [& ms]
  {:match
   (fn [a] (reduce (fn [x y] (and x y))
                          (map #((:match %) a) ms)))
   :description
   (describe-list "all-of" (map :description ms))
   :describe-mismatch
   standard-describe-mismatch})

(defn any-of
  "passes if any of the given matchers pass:

  (matcha/run-match (matcha/any-of (matcha/= 1) (matcha/= 2)) 2) ; => passes
  (matcha/run-match (matcha/any-of (matcha/= 3) (matcha/= 2)) 1) ; => fails"
  [& ms]
  {:match
   (fn [a]
     (reduce (fn [x y] (or x y))
             (map #((:match %) a) ms)))

   :description
   (describe-list "any-of" (map :description ms))

   :describe-mismatch
   standard-describe-mismatch})

(defn some
  [m]
  "passes if the matcher given passes on any of the items of the sequence this matcher receives
  (matcha/run-match (matcha/some (matcha/= 1)) [1]) ; => passes
  (matcha/run-match (matcha/some (matcha/= 0)) [1]) ; => fails"
  {:match
   (fn [xs] (core/some #((:match m) %) xs))
   :description
   (describe-list "some" [(:description m)])
   :describe-mismatch
   standard-describe-mismatch})

(defn every?
  [m]
  "passes if the matcher given passes against any of the items of the sequence the resulting matcher receives"
  {:match
   (fn [xs] (core/every? #((:match m) %) xs))
   :description
   (describe-list "every?" [(:description m)])
   :describe-mismatch
   standard-describe-mismatch})

(defn has-count
  "passes if the sequence received has the given count
  (matcha/run-match (matcha/has-count 1) [1]) ; => passes
  (matcha/run-match (matcha/has-count 2) [])  ; => fails"
  [n]
  {:match (fn [xs] (clojure.core/= (count xs) n))
   :description (describe-list "has-count" [n])
   :describe-mismatch
   standard-describe-mismatch})

(defn includes
  "passes if the sequence received includes the given item

  (matcha/run-match (matcha/includes 1) [1]) ; => passes
  (matcha/run-match (matcha/includes 1) [2]) ; => fails"
  [x]
  {:match
   (fn [xs] (core/some #{x} xs))
   :description
   (describe-list "includes" [x])
   :describe-mismatch
   standard-describe-mismatch})

(defn not
  "passes if the given matcher fails
  (matcha/run-match (matcha/not (matcha/= 1)) 1) ; => passes
  (matcha/run-match (matcha/not (matcha/= 2)) 1) ; => fails"
  [m]
  {:match #(core/not ((:match m) %))
   :description (describe-list "not" [m])
   :describe-mismatch
   standard-describe-mismatch})

(defn describe-class-mismatch [x]
  (str "was " (pr-str x) " <" (class x) ">"))

(defn instance?
  "passes if the value matches the given class
  (matcha/run-match (matcha/instance? clojure.lang.Keyword) :foo) ; => passes
  (matcha/run-match (matcha/instance? clojure.lang.Keyword) 1) ; => fails"
  [klazz]
  {:match #(core/instance? klazz %)
   :description (describe-list "instance?" [klazz])
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a string
    (matcha/run-match matcha/string? \"foo\") ; => passes
    (matcha/run-match matcha/string? 1) ; => fails"}
  string?
  {:match core/string?
   :description "(string?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a map
    (matcha/run-match matcha/map? {}) ; => passes
    (matcha/run-match matcha/map? 1) ; => fails"}
  map?
  {:match core/map?
   :description "(map?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a seq
    (matcha/run-match matcha/seq? {}) ; => passes
    (matcha/run-match matcha/seq? 1) ; => fails"}
  seq?
  {:match core/seq?
   :description "(seq?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a char
    (matcha/run-match matcha/char? {}) ; => passes
    (matcha/run-match matcha/char? 1) ; => fails"}
  char?
  {:match core/char?
   :description "(char?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a vector
    (matcha/run-match matcha/vector? {}) ; => passes
    (matcha/run-match matcha/vector? 1) ; => fails"}
  vector?
  {:match core/vector?
   :description "(vector?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a keyword
    (matcha/run-match matcha/keyword? :foo) ; => passes
    (matcha/run-match matcha/keyword? 1) ; => fails"}
  keyword?
  {:match core/keyword?
   :description "(keyword?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is a symbol
    (matcha/run-match matcha/symbol? 'foo) ; => passes
    (matcha/run-match matcha/symbol? 1) ; => fails"}
  symbol?
  {:match core/symbol?
   :description "(symbol?)"
   :describe-mismatch describe-class-mismatch})

(def
  ^{:doc
    "passes if the value is nil
    (matcha/run-match matcha/nil? nil) ; => passes
    (matcha/run-match matcha/nil? 1) ; => fails"}
  nil?
  {:match core/nil?
   :description "(nil?)"
   :describe-mismatch describe-class-mismatch})

(defn assert-good-matcher [{:keys [match description describe-mismatch] :as matcher}]
  (assert (not (nil? matcher)) "Matcher should not be nil")
  (assert match
          (str "matcher should have a :match key. Matcher: " matcher))
  (assert description
          (str "matcher should have a :description key. Matcher: " matcher))
  (assert describe-mismatch
          (str "matcher should have a :describe-mismatch key. Matcher: " matcher))

  (assert (core/= 0 (count (dissoc matcher :match :description :describe-mismatch)))
          (str "matcher shouldn't have extra keys, but had "
               (describe-list "" (dissoc matcher :match :description :describe-mismatch)))))

(defn run-match
  "runs a matcher, given a value to match against.
  Returns a map:

  if the matcher matches the value:
  {:pass? true}

  if the matcher fails:
  {:pass? false
  :expected (a string)
  :was (a string)}

  the results can be made human readable with (format-message result)"
  [matcher a]
  (assert-good-matcher matcher)
  (if ((:match matcher) a)
    {:pass? true}
    {:pass? false
     :expected (:description matcher)
     :was ((:describe-mismatch matcher) a)}))