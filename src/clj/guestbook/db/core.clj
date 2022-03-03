(ns guestbook.db.core
  (:require
    [java-time :refer [java-date]]
    [next.jdbc.date-time]
    [next.jdbc.result-set]
    [conman.core :as conman]
    [mount.core :refer [defstate]]
    [guestbook.config :refer [env]]
    [next.jdbc.prepare]
    [jsonista.core :as json])
  (:import org.postgresql.util.PGobject
           (clojure.lang IPersistentMap IPersistentVector)
           (java.sql PreparedStatement Time Date Timestamp)
           (java.time ZoneId)))

(defstate ^:dynamic *db*
          :start (conman/connect! {:jdbc-url (env :database-url)})
          :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defn sql-timestamp->inst [t]
  (-> t
      (.toLocalDateTime)
      (.atZone (ZoneId/systemDefault))
      (java-date)))

(defn read-pg-object [^PGobject obj]
  (cond-> (.getValue obj)
          (#{"json" "jsonb"} (.getType obj))
          (json/read-value json/keyword-keys-object-mapper)))

(extend-protocol next.jdbc.result-set/ReadableColumn
  Timestamp
  (read-column-by-label [^Timestamp v _]
    (sql-timestamp->inst v))
  (read-column-by-index [^Timestamp v _2 _3]
    (sql-timestamp->inst v))
  Date
  (read-column-by-label [^Date v _]
    (.toLocalDate v))
  (read-column-by-index [^Date v _2 _3]
    (.toLocalDate v))
  Time
  (read-column-by-label [^Time v _]
    (.toLocalTime v))
  (read-column-by-index [^Time v _2 _3]
    (.toLocalTime v))
  PGobject
  (read-column-by-label [^PGobject v _]
    (read-pg-object v))
  (read-column-by-index [^PGobject v _2 _3]
    (read-pg-object v)))

(defn write-pg-object [v]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string v))))

(extend-protocol next.jdbc.prepare/SettableParameter
  IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (write-pg-object m)))
  IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (write-pg-object v))))