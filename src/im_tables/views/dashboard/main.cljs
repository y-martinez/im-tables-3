(ns im-tables.views.dashboard.main
  (:require [im-tables.views.dashboard.pagination :as pager]
            [im-tables.views.dashboard.manager.columns.main :as column-manager]
            [im-tables.views.dashboard.undo :as undo]
            [im-tables.views.dashboard.save :as save]))




(defn main []
  (fn [loc response pagination]
    (.log js/console "Pager loc" response)
    [:div.container-fluid
     [:div.row
      [column-manager/main]
      [:div.col-xs-12
       [:button.btn.btn-primary
        {:data-toggle "modal"
         :data-target "#myModal"}
        "Add Columns"]]]
     [:div.row
      [:div.col-xs-6
       [:span (str "Showing "
                   (inc (:start pagination)) " to "
                   (+ (:start pagination) (:limit pagination)) " of "
                   (:iTotalRecords response) " rows")]]
      [:div.col-xs-6
       [:div.container-fluid
        [:div.row
         [:div.col-xs-2 [:div.btn-toolbar
                         [save/main response]
                         [undo/main]]]
         [:div.col-xs-10 [:div.pull-right
                          [pager/main loc (merge pagination
                                             {:total (get response :iTotalRecords)})]]]]]]]]))

