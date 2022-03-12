(ns guestbook.handler-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [guestbook.handler :refer :all]
    [guestbook.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]
    [buddy.hashers :as hashers])
  (:import (java.util Date)))

(def now (Date. 0))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(defn mock-user [{:keys [login]}]
  (when (= login "foo")
    {:login      "foo"
     :password   (hashers/encrypt "password")
     :created_at now
     :profile    {}}))

(defn login-request [login pass]
  (-> (request :post "/api/login")
      (json-body,,, {:login    login
                     :password pass})))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'guestbook.config/env
                 #'guestbook.routes.websockets/socket
                 #'guestbook.handler/routes)
    (f)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest test-login
  (with-redefs [guestbook.db.core/get-user-for-auth* mock-user]
    (let [handler (app)]
      (testing "login success"
        (let [{:keys [status body]} (handler (login-request "foo" "password"))
              json (parse-json body)]
          (println json)
          (is (= 200 status))
          (is (= "foo" (:login (:identity json))))))
      (testing "password mismatch"
        (let [{:keys [status]} (handler (login-request "foo" "whatever"))]
          (is (= 401 status)))))))
