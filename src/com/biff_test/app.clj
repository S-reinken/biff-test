(ns com.biff-test.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.biff-test.middleware :as mid]
            [com.biff-test.ui :as ui]
            [com.biff-test.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]
            [cheshire.core :as cheshire]))

(import java.util.UUID)
(defn set-foo [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
                  [{:db/op :update
                    :db/doc-type :user
                    :xt/id (:uid session)
                    :user/foo (:foo params)}])
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
   {:hx-post "/app/set-bar"
    :hx-swap "outerHTML"}
   [:label.block {:for "bar"} "Bar: "
    [:span.font-mono (pr-str value)]]
   [:.h-1]
   [:.flex
    [:input.w-full.text-black#bar {:type "text" :name "bar" :value value}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]
   [:.h-1]
   [:.text-sm.text-gray-600
    "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [session params] :as ctx}]
  (biff/fix-print (println "Setting bar"))
  (biff/submit-tx ctx
                  [{:db/op :update
                    :db/doc-type :user
                    :xt/id (:uid session)
                    :user/bar (:bar params)}])
  (biff/render (bar-form {:value (:bar params)})))

(defn message [{:msg/keys [text sent-at]}]
  [:.mt-3 {:_ "init send newMessage to #message-header"}
   [:.text-gray-600 (biff/format-date sent-at "dd MMM yyyy HH:mm:ss")]
   [:div text]])





(defn send-message [{:keys [session] :as ctx} {:keys [text]}]
  (let [{:keys [text]} (cheshire/parse-string text true)]
    (biff/submit-tx ctx
                    [{:db/doc-type :msg
                      :msg/user (:uid session)
                      :msg/text text
                      :msg/sent-at :db/now}])))


(defn chat [{:keys [biff/db]}]
  (let [messages (q db
                    '{:find (pull msg [*])
                      :in [t0]
                      :where [[msg :msg/sent-at t]
                              [(<= t0 t)]]}
                    (biff/add-seconds (java.util.Date.) (* -60 10)))]
    [:div {:hx-ext "ws" :ws-connect "/app/chat"}
     [:form.mb-0 {:ws-send true
                  :_ "on submit set value of #message to ''"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full.text-black#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]]]
     [:.h-6]
     [:div#message-header
      {:_ "on newMessage put 'Messages sent in the past 10 minutes:' into me"}
      (if (empty? messages)
        "No messages yet."
        "Messages sent in the past 10 minutes:")]
     [:div#messages
      (map message (sort-by :msg/sent-at #(compare %2 %1) messages))]]))

(defn nav-bar [email]
  [:<>
   [:.div.flex.flex-row.items-center
    [:a.link {:href "/app"} "Main App"]
    [:.w-3]
    [:a.link {:href "/app/trello"} "Trello Clone"]
    [:.w-3]
    [:a.link {:href "/app/other"} "Other Experiments"]]
   [:div "Signed in as " email ". "
    (biff/form
     {:action "/auth/signout"
      :class "inline"}
     [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
      "Sign out"])
    "."]])

(defn app [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email foo bar]} (xt/entity db (:uid session))]
    (ui/page
     {}
     [:div.flex.flex-col.items-left
      (nav-bar email)
      [:.h-6]
      (biff/form
       {:action "/app/set-foo"}
       [:label.block {:for "foo"} "Foo: "
        [:span.font-mono (pr-str foo)]]
       [:.h-1]
       [:.flex
        [:input.w-full.text-black#foo {:type "text" :name "foo" :value foo}]
        [:.w-3]
        [:button.btn {:type "submit"} "Update"]]
       [:.h-1]
       [:.text-sm.text-gray-600
        "This demonstrates updating a value with a plain old form."])
      [:.h-6]
      (bar-form {:value bar})
      [:.h-6]
      (chat ctx)])))

(defn card [card-data]
  [:div.bg-gray-700.p-1.width-32.select-none
   {:uuid (:xt/id card-data)
    :title (:card/title card-data)}
   (:card/title card-data)])

(defn board [lists]
  [:div.flex.flex-row.gap-x-1#board
   (for [list (sort-by :list/pos lists)]
     [:div.flex.flex-col.bg-gray-900.p-1.gap-y-1.w-32
      {:x-init 
       (str "new Sortable($el, 
                { animation: 150,
                  group: 'shared',
                  onEnd: (evt) => {
                    let destinationCards = Array.from(evt.to.children).map((child, index) => 
                      ({
                        title: child.getAttribute('title'),
                        id: child.getAttribute('uuid'),
                        position: index
                      }));
                    let sourceCards = Array.from(evt.from.children).map((child, index) => 
                      ({
                        title: child.getAttribute('title'),
                        id: child.getAttribute('uuid'),
                        position: index
                      }));
                    let sourceListId = evt.from.getAttribute('uuid');
                    let destinationListId = evt.to.getAttribute('uuid');
                    fetch(
                      '/app/trello/move-card',
                      {
                        headers: { 'X-CSRF-Token': csrfToken,
                                   'Content-Type': 'application/json' },
                        method: 'POST',
                        body: JSON.stringify({
                          sourceListId: sourceListId,
                          destinationListId: destinationListId,
                          sourceCards: sourceCards,
                          destinationCards: destinationCards
                        })
                      })
                  }})")
       :uuid (:xt/id list)}
      (:list/title list)
      (for [card-data (sort-by :card/pos (:card/_list list))]
        (card card-data))])])

(defn trello-clone [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        lists (q db
                 '{:find (pull ?list [* {:card/_list [*]}])
                   :where [[?list :list/title]]})]
    (ui/page
     {}
     [:div.flex.flex-col.items-left
      (nav-bar email)
      [:div "Here goes the trello clone"]
      [:div.flex.flex-col#board-container
       {:hx-ext "ws" :ws-connect "/app/trello/trello-board"
        :x-data (str "{csrfToken: '" anti-forgery/*anti-forgery-token* "' }")}
       (board lists)]])))

(defn other-stuff [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     (nav-bar email)
     [:div "Here goes the other stuff"])))

(defn ws-handler [{:keys [com.biff-test/chat-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text-message]
                   (send-message ctx {:ws ws :text text-message}))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(defn trello-board [{:keys [session biff/db com.biff-test/trello-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! trello-clients conj ws))
        :on-close (fn [ws status-code reason]
                    (swap! trello-clients disj ws))}})

(defn notify-trello-clients [{:keys [com.biff-test/trello-clients biff/db]} tx]
  (biff/fix-print (println "Notifying trello clients"))
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :card/title)
          :let [lists (q db
                         '{:find (pull ?list [* {:card/_list [*]}])
                           :where [[?list :list/title]]})
                html (rum/render-static-markup
                      [:div#board-container {:hx-swap-oob "innerHTML"}
                       (board lists)])]
          ws @trello-clients]
    (jetty/send! ws html)))

(defn notify-clients [{:keys [com.biff-test/chat-clients] :as ctx} tx]
  (notify-trello-clients ctx tx)
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :msg/text)
          :let [html (rum/render-static-markup
                      [:div#messages {:hx-swap-oob "afterbegin"}
                       (message doc)])]
          ws @chat-clients]
    (jetty/send! ws html)))

(defn print-ret [val]
  (println val)
  val)

(defn move-card [{:keys [params] :as ctx}]
  (def *ctx ctx)
  (biff/fix-print (println (str "Moving card: " params)))
  (let [{:keys [sourceListId
                destinationListId
                sourceCards
                destinationCards]} params]
    (biff/submit-tx
     ctx
     (concat
      (for [card-data sourceCards]
        {:db/doc-type :card
         :xt/id (UUID/fromString (:id card-data))
         :card/list (UUID/fromString sourceListId)
         :card/pos (:position card-data)
         :card/title (:title card-data)})
      (for [card-data destinationCards]
        {:db/doc-type :card
         :xt/id (UUID/fromString (:id card-data))
         :card/list (UUID/fromString destinationListId)
         :card/pos (:position card-data)
         :card/title (:title card-data)}))))
  {:status 204})

(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def module
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/trello"
             ["" {:get trello-clone}]
             ["/trello-board" {:get trello-board}]
             ["/move-card" {:post move-card}]]
            ["/other" {:get other-stuff}]
            ["/set-foo" {:post set-foo}]
            ["/set-bar" {:post set-bar}]
            ["/chat" {:get ws-handler}]]
   :api-routes [["/api/echo" {:post echo}]]
   :on-tx notify-clients})

(comment
  
  (:body *ctx))