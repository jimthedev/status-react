(ns status-im.chat.views.input.input
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [taoensso.timbre :as log]
            [status-im.accessibility-ids :as id]
            [status-im.components.react :refer [view
                                                text
                                                text-input
                                                icon
                                                touchable-highlight
                                                dismiss-keyboard!]]
            [status-im.chat.views.input.emoji :as emoji]
            [status-im.chat.views.input.parameter-box :as parameter-box]
            [status-im.chat.views.input.suggestions :as suggestions]
            [status-im.chat.styles.input.input :as style]
            [status-im.chat.utils :as utils]))

(defview commands-view []
  []
  [view style/commands-root
   [touchable-highlight {:on-press #(dispatch [:toggle-chat-ui-props :show-suggestions?])}
    [view
     [icon :input_list style/commands-list-icon]]]
   [view style/commands
    [text {:style style/command
           :font  :roboto-mono}
     "/browse"]
    [text {:style style/command
           :font  :roboto-mono}
     "/send"]]])

(defn input-helper [{:keys [command width]}]
  (when (and command
             (empty? (:args command)))
    (when-let [placeholder (get-in command [:command :params 0 :placeholder])]
      [text {:style (style/input-helper-text width)}
       placeholder])))

(defn input-view [_]
  (let [component         (r/current-component)
        set-layout-width  #(r/set-state component {:width %})
        set-layout-height #(r/set-state component {:height %})
        default-value     (subscribe [:chat :input-text])]
    (r/create-class
      {:component-will-mount
       (fn []
         (dispatch [:update-suggestions]))

       :reagent-render
       (fn [{:keys [command]}]
         (let [{:keys [width height]} (r/state component)]
           [view (style/input-root height command)
            [text-input {:accessibility-label    id/chat-message-input
                         :blur-on-submit         true
                         :default-value          @default-value
                         :multiline              true
                         :on-blur                #(do (dispatch [:set-chat-ui-props :input-focused? false])
                                                      (set-layout-height 0))
                         :on-change-text         #(do (dispatch [:set-chat-input-text %])
                                                      (dispatch [:load-chat-parameter-box command]))
                         :on-content-size-change #(let [h (-> (.-nativeEvent %)
                                                              (.-contentSize)
                                                              (.-height))]
                                                    (set-layout-height h))
                         :on-submit-editing      #(dispatch [:send-chat-message])
                         :on-focus               #(do (dispatch [:set-chat-ui-props :input-focused? true])
                                                      (dispatch [:set-chat-ui-props :show-emoji? false]))
                         :style                  style/input-view}]
            [text {:style     style/invisible-input-text
                   :on-layout #(let [w (-> (.-nativeEvent %)
                                           (.-layout)
                                           (.-width))]
                                 (set-layout-width w))}
             (utils/safe-trim @default-value)]
            [input-helper {:command command
                           :width   width}]
            (when-not command
              [touchable-highlight {:on-press #(do (dispatch [:toggle-chat-ui-props :show-emoji?])
                                                   (dismiss-keyboard!))}
               [view
                [icon :smile style/input-emoji-icon]]])]))})))

(defview container []
  [margin [:chat-input-margin]
   show-emoji? [:chat-ui-props :show-emoji?]
   chat-parameter-box [:chat-parameter-box]
   show-suggestions? [:chat-ui-props :show-suggestions?]
   selected-chat-command [:selected-chat-command]]
  [view
   (when chat-parameter-box
     [parameter-box/parameter-box-view])
   (when show-suggestions?
     [suggestions/suggestions-view])
   [view {:style     (style/root margin)
          :on-layout #(let [h (-> (.-nativeEvent %)
                                  (.-layout)
                                  (.-height))]
                        (dispatch [:set-chat-ui-props :input-height h]))}
    [view (style/container selected-chat-command)
     (when-not selected-chat-command
       [commands-view])
     [input-view selected-chat-command]]
    (when show-emoji?
      [emoji/emoji-view])]])