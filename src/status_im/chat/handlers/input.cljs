(ns status-im.chat.handlers.input
  (:require [re-frame.core :refer [enrich after dispatch]]
            [taoensso.timbre :as log]
            [status-im.chat.constants :as const]
            [status-im.chat.models.input :as input-model]
            [status-im.chat.suggestions :as suggestions]
            [status-im.components.status :as status]
            [status-im.utils.handlers :as handlers]
            [clojure.string :as str]))

(handlers/register-handler
  :set-chat-input-text
  (fn [{:keys [current-chat-id] :as db} [_ text]]
    (dispatch [:update-suggestions current-chat-id text])
    (assoc-in db [:chats current-chat-id :input-text] text)))

(handlers/register-handler
  :select-chat-input-command
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ {:keys [name] :as command}]]
      (dispatch [:set-chat-input-text (str const/command-char name const/spacing-char)])
      (dispatch [:set-chat-ui-props :show-suggestions? false])
      (dispatch [:load-chat-parameter-box command 0]))))

(handlers/register-handler
  :set-command-argument
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ [index arg]]]
      (let [command      (-> (get-in db [:chats current-chat-id :input-text])
                             (str/split const/spacing-char))
            command-name (first command)
            command-args (into [] (rest command))
            command-args (if (< index (count command-args))
                           (assoc command-args index arg)
                           (conj command-args arg))]
        (dispatch [:set-chat-input-text (str command-name
                                             const/spacing-char
                                             (str/join const/spacing-char command-args)
                                             const/spacing-char)])
        (dispatch [:send-if-command-complete])))))

(handlers/register-handler
  :update-suggestions
  (fn [{:keys [current-chat-id] :as db} [_ chat-id text]]
    (let [chat-id     (or chat-id current-chat-id)
          chat-text   (or text (get-in db [:chats chat-id :input-text]) "")
          suggestions (suggestions/get-suggestions db chat-text)
          {:keys [dapp?]} (get-in db [:contacts chat-id])]
      (assoc-in db [:chats chat-id :command-suggestions] suggestions))))

(handlers/register-handler
  :load-chat-parameter-box
  (handlers/side-effect!
    (fn [{:keys [current-chat-id] :as db} [_ {:keys [name type] :as command}]]
      (let [parameter-index (input-model/argument-position db)]
        (when (and command (> parameter-index -1))
          (let [data   (get-in db [:local-storage current-chat-id])
                path   [(if (= :command type) :commands :responses)
                        name
                        :params
                        parameter-index
                        :suggestions]
                args   (-> (get-in db [:chats current-chat-id :input-text])
                           (str/split const/spacing-char)
                           (rest))
                params {:parameters {:args args}
                        :context    {:data data}}]
            (status/call-jail current-chat-id
                              path
                              params
                              #(dispatch [:suggestions-handler
                                          {:chat-id         current-chat-id
                                           :command         command
                                           :parameter-index parameter-index
                                           :result          %}]))))))))

(handlers/register-handler
  :send-if-command-complete
  (handlers/side-effect!
    (fn [db]
      (when (input-model/command-complete? db)
        (dispatch [:send-chat-message])))))