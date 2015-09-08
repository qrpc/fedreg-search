(ns fedreg-search 
  (:import [goog.net Jsonp])
  (:require
      [google-vis :as gv]
      [fedreg-time :as ft]))

(.load js/google "visualization" "1" (clj->js {:packages ["table"]})) ;macro or function
(def my-url "https://www.federalregister.gov/api/v1/articles.json?per_page=500&order=relevance&fields%5B%5D=action&fields%5B%5D=agency_names&fields%5B%5D=dates&fields%5B%5D=docket_id&fields%5B%5D=publication_date&fields%5B%5D=title&fields%5B%5D=topics&fields%5B%5D=type&fields%5B%5D=comments_close_on&fields%5B%5D=html_url&conditions%5Bagencies%5D%5B%5D=environmental-protection-agency&conditions%5Bagencies%5D%5B%5D=nuclear-regulatory-commission&conditions%5Bagencies%5D%5B%5D=mine-safety-and-health-administration&conditions%5Bagencies%5D%5B%5D=federal-energy-regulatory-commission&conditions%5Bagencies%5D%5B%5D=engineers-corps&conditions%5Bagencies%5D%5B%5D=surface-mining-reclamation-and-enforcement-office&conditions%5Bagencies%5D%5B%5D=energy-department")

; Agencies to search for
(def agencies { "EPA"   "environmental-protection-agency"
                "NRC"   "nuclear-regulatory-commission"
                "MSHA"  "mine-safety-and-health-administration"
                "FERC"  "federal-energy-regulatory-commission"
                "Army Corps" "engineers-corps"
                "OSM"   "surface-mining-reclamation-and-enforcement-office"
                "DOE"   "energy-department"})
(defn build-url
  "combine agencies into the url"
  [url my-keys]
  (if (= 0 (count my-keys))
    url
    (do
      (let [agency (first my-keys)]
      (recur (str url "&conditions%5Bagencies%5D%5B%5D=" (agencies agency)) (rest my-keys))))))

(defn get-agency-abbr
  [in-vector out-str]
  (if (empty? in-vector)
    out-str
   (let
      [agency-to-abbr
       {"Environmental Protection Agency" "EPA"
       "Nuclear Regulatory Commission" "NRC"
       "Mine Safety and Health Administration" "MSHA"
       "Federal Energy Regulatory Commission" "FERC"
       "Engineers Corps" "Army Corps"
       "Surface Mining Reclamation and Enforcement Office" "OSM"
       "Energy Department" "DOE"}]
    
      (if-let [agency (get agency-to-abbr (first in-vector))]
        (recur (rest in-vector) (str out-str " " agency))
        (recur (rest in-vector) out-str)))))

;; write the initial HTML
;; elements we will set at runtime
(def data-element (.getElementById js/document "datatable"))
(def dates-element (.getElementById js/document "dates"))


(set! data-element.innerHTML "Data loading...<br>  Please wait.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; interface with google visualization 

(defn build-chart
  [in-vector]
(gv/draw-chart
  [["string" "Title"] ["string" "Action"]
   ["string" "Agency"] ["string" "Docket ID"]
   ["date" "Comments Close"] ["date" "Publication Date"]]
  (clj->js in-vector) 
  (clj->js {:allowHtml true :width "100%" :sortColumn 4})
  (new js/google.visualization.Table data-element))
)

(defn filter-results
  [in-vector out-vector]
  (if (empty? in-vector)
    out-vector
      (let 
        [my-map (first in-vector)
        comments-close 
         (ft/federal-to-js (get my-map "comments_close_on"))]
         (if (clojure.string/blank? comments-close)
          (recur (rest in-vector) out-vector) 
         (recur 
          (rest in-vector)
          (conj out-vector
            (vector 
              (get my-map "title")
              (get my-map "action")
              (get-agency-abbr 
                   (get my-map "agency_names") "")
              (str "<a href =\""
                   (get my-map "html_url")
                   "\">"
                    (if-let [docket (get my-map "docket_id")]
                      docket
                      "No Docket Id.")
                    "</a>")
             comments-close
              (ft/federal-to-js (get my-map "publication_date"))
              )))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ajax code 

(defn handler 
  "Handle the ajax response"
  [response]
  (let 
    [clj-resp (js->clj response {:kewordize-keys true})]
    
    (.setOnLoadCallback js/google (fn []
                                    (build-chart
                                      (filter-results (get clj-resp "results") []))))))

(defn err-handler 
  "Handle the ajax errors"
  [response]
    (.log js/console (str "ERROR: " response)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; main

(set! dates-element.innerHTML (str (ft/my-time-string ft/us-formatter 1) " to " (ft/my-time-string ft/us-formatter)))

(let
  [url (str my-url (build-url "" (keys agencies))
              (ft/one-week-ago))]

;(set! data-element.innerHTML url)

(.send (goog.net.Jsonp. url nil)
  "" handler err-handler nil)


)

