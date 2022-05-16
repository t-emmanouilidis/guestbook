(ns guestbook.re-frame-utils
  (:require [re-frame.core :as rf]))

(def std-interceptors
  [(when ^boolean goog.DEBUG rf/debug)])

(def <sub (comp deref rf/subscribe))

(def >evt rf/dispatch)