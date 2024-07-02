(ns com.biff-test.schema)

(def schema
  {:user/id :uuid
   :list/id :uuid
   :card/id :uuid
   
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]
          [:user/foo {:optional true} :string]
          [:user/bar {:optional true} :string]]
   :list [:map {:closed true}
          [:xt/id       :list/id]
          [:list/title  :string]
          [:list/pos    :int]]
   
   :card [:map {:closed true}
          [:xt/id       :card/id]
          [:card/title  :string]
          [:card/list   :list/id]
          [:card/pos    :int]]

   :msg/id :uuid
   :msg [:map {:closed true}
         [:xt/id       :msg/id]
         [:msg/user    :user/id]
         [:msg/text    :string]
         [:msg/sent-at inst?]]})

(def module
  {:schema schema})
