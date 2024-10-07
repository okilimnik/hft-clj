(ns hft.chart
  (:import (org.jfree.chart JFreeChart)
           (org.jfree.chart.axis DateAxis NumberAxis)
           (org.jfree.chart.plot CombinedDomainXYPlot XYPlot)
           [org.jfree.chart.renderer.xy CandlestickRenderer]
           [org.jfree.data.xy DefaultHighLowDataset]))

(defn convert [klines]
  (let [nb-bars (count klines)]
    (DefaultHighLowDataset. name
                            (into-array (map :t klines))
                            (into-array (map :h klines))
                            (into-array (map :l klines))
                            (into-array (map :o klines))
                            (into-array (map :c klines))
                            (into-array (map :v klines)))))

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