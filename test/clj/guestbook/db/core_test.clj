(ns guestbook.db.core-test
  (:require
    [guestbook.db.core :refer [*db*] :as db]
    [java-time.pre-java8]
    [luminus-migrations.core :as migrations]
    [clojure.test :refer :all]
    [next.jdbc :as jdbc]
    [guestbook.config :refer [env]]
    [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'guestbook.config/env
      #'guestbook.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-users
  (jdbc/with-transaction
    [t-conn *db* {:rollback-only true}]
    (is (= 1 (db/create-user!* t-conn
                               {:login    "foo"
                                :password "password"})))
    (is (= {:login   "foo"
            :profile {}}
           (dissoc (db/get-user* t-conn {:login "foo"}) :created_at)))))