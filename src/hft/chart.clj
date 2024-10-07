(ns hft.chart
  (:require [hft.image :as i])
  (:import [java.util Date]
           (org.jfree.chart JFreeChart)
           (org.jfree.chart.axis DateAxis NumberAxis)
           (org.jfree.chart.plot CombinedDomainXYPlot XYPlot)
           [org.jfree.chart.renderer.xy CandlestickRenderer]
           [org.jfree.data.time Second TimeSeries TimeSeriesCollection]
           [org.jfree.data.xy DefaultHighLowDataset]
           [org.ta4j.core Indicator]))

(defmulti convert class)

(defmethod convert [::collection String] [klines name]
  (DefaultHighLowDataset. name
                          (into-array (map :t klines))
                          (into-array (map :h klines))
                          (into-array (map :l klines))
                          (into-array (map :o klines))
                          (into-array (map :c klines))
                          (into-array (map :v klines))))

(defmethod convert [Indicator String] [indicator name]
  (let [collection (TimeSeriesCollection.)
        timeseries (TimeSeries. name)
        bar-series (.getBarSeries indicator)]
    (dotimes [i (.getBarCount (.getBarSeries indicator))]
      (let [bar (.getBar bar-series i)
            value (.doubleValue (.getValue indicator i))
            time (Second. (Date. (* 1000 (.toEpochSecond (.getEndTime bar)))))]
        (.add timeseries time value)))
    (.addSeries collection timeseries)
    collection))

(defn ->chart [name klines]
  (let [time-axis (DateAxis. "Time")
        value-axis (NumberAxis. "Price/Value")
        renderer (CandlestickRenderer.)
        bar-series-data (convert klines)
        plot (XYPlot. bar-series-data nil value-axis renderer)
        combined-domain-plot (CombinedDomainXYPlot. time-axis)]
    (.add combined-domain-plot plot 10)
    (.setAutoRangeIncludesZero value-axis false)
    (.setAutoWidthMethod renderer 1)
    (.setDrawVolume renderer false)
    (.setDefaultItemLabelsVisible renderer false)
    (let [chart (JFreeChart. name JFreeChart/DEFAULT_TITLE_FONT combined-domain-plot true)]
      chart)))

(defn with-indicator [chart indicator plot-type chart-type]
  (let [^CombinedDomainXYPlot plot (.getPlot chart)
        counter (.getBarCount (.getBarSeries indicator))]
    (cond
      (= plot-type PlotType/OVERLAY) (cond
                                       (= chart-type ChartType/LINE)
                                       (let [^TimeSeriesCollection timeseries (convert indicator)])))))

(defn ->image [chart filepath]
  (let [image (.createBufferedImage chart 600 800)]
    (i/save image filepath)))