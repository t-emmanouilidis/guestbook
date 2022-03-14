(ns guestbook.core-test
  (:require [cljs.test :refer-macros [is are deftest testing use-fixtures]]
            [pjstadig.humane-test-output]
            [reagent.dom.server :as dom]
            [guestbook.components :as gc]))

(deftest test-md
  (is (= "<p class=\"markdown\" data-reactroot=\"\"><h3>Hello</h3></p>"
         (dom/render-to-string (gc/md "### Hello")))))