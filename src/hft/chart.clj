(ns hft.chart
  (:require [hft.image :as i])
  (:import [java.awt Color]
           [java.awt.geom Ellipse2D$Double]
           [java.util Date]
           [org.jfree.chart JFreeChart]
           [org.jfree.chart.axis DateAxis NumberAxis]
           [org.jfree.chart.plot CombinedDomainXYPlot XYPlot]
           [org.jfree.chart.renderer.xy
            CandlestickRenderer
            StandardXYBarPainter
            XYBarRenderer
            XYLineAndShapeRenderer]
           [org.jfree.data.time Second TimeSeries TimeSeriesCollection]
           [org.jfree.data.xy DefaultHighLowDataset]))

(defmulti convert (fn [kind & _args] kind))

(defmethod convert :raw [_ klines name]
  (DefaultHighLowDataset. name
                          (into-array (Date. (map :t klines)))
                          (into-array (map :h klines))
                          (into-array (map :l klines))
                          (into-array (map :o klines))
                          (into-array (map :c klines))
                          (into-array (map :v klines))))

(defmethod convert :line [_ indicator name]
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

(defmethod convert :bar [_ indicator name]
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
        bar-series-data (convert :raw klines name)
        plot (XYPlot. bar-series-data nil value-axis renderer)
        combined-domain-plot (CombinedDomainXYPlot. time-axis)]
    (.add combined-domain-plot plot 10)
    (.setAutoRangeIncludesZero value-axis false)
    (.setAutoWidthMethod renderer 1)
    (.setDrawVolume renderer false)
    (.setDefaultItemLabelsVisible renderer false)
    (let [chart (JFreeChart. name JFreeChart/DEFAULT_TITLE_FONT combined-domain-plot true)]
      chart)))

(defn create-line-renderer [color]
  (let [renderer (XYLineAndShapeRenderer.)]
    (.setSeriesShape renderer 0 (Ellipse2D$Double. -2.0 -2.0 4.0 4.0))
    (.setSeriesPaint renderer 0 color)
    renderer))

(defn create-bar-renderer [color]
  (doto (XYBarRenderer.)
    (.setBarPainter (StandardXYBarPainter.))
    (.setSeriesPaint 0 color)
    (.setShadowVisible false)))

(defn get-indicator-color [name]
  (case name
    "IchimokuChikouSpanIndicator" Color/GREEN
    "IchimokuKijunSenIndicator" Color/RED
    Color/BLACK))

(defn with-indicator [chart indicator plot-type chart-type]
  (let [plot (.getPlot chart)
        counter (.getBarCount (.getBarSeries indicator))
        name (.toString indicator)
        color (get-indicator-color name)]
    (cond
      (= plot-type :overlay) (cond
                               (= chart-type :line)
                               (let [timeseries (convert :line indicator name)
                                     renderer (create-line-renderer color)
                                     candlestick-plot (.get (.getSubplots plot) 0)]
                                 (.setRenderer candlestick-plot counter renderer)
                                 (.setDataset candlestick-plot counter timeseries))

                               (= chart-type :bar)
                               (let [bar-dataset (convert :bar indicator name)
                                     renderer (create-bar-renderer color)
                                     candlestick-plot (.get (.getSubplots plot) 0)]
                                 (.setRenderer candlestick-plot counter renderer)
                                 (.setDataset candlestick-plot counter bar-dataset)))
      (= plot-type :subplot) (cond
                               (= chart-type :line)
                               (let [timeseries (convert :line indicator name)
                                     renderer (create-line-renderer color)
                                     value-axis (NumberAxis. name)
                                     line-plot (XYPlot. timeseries nil value-axis renderer)]
                                 (.setLabel value-axis "")
                                 (.setAutoRangeIncludesZero value-axis false)
                                 (.add plot line-plot 1))

                               (= chart-type :bar)
                               (let [bar-dataset (convert :bar indicator name)
                                     value-axis (NumberAxis. name)
                                     bar-renderer (create-bar-renderer color)
                                     bar-plot (XYPlot. bar-dataset nil value-axis bar-renderer)]
                                 (.setLabel value-axis "")
                                 (.add plot bar-plot 1)))))
  chart)

(defn ->image [chart filepath]
  (let [image (.createBufferedImage chart 600 800)]
    (i/save image filepath)))