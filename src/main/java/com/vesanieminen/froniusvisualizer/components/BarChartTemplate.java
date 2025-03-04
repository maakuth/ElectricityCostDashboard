package com.vesanieminen.froniusvisualizer.components;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.PropertyDescriptor;
import com.vaadin.flow.component.PropertyDescriptors;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vesanieminen.froniusvisualizer.services.model.NordpoolPrice;
import com.vesanieminen.froniusvisualizer.util.Utils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;

import static com.vesanieminen.froniusvisualizer.services.NordpoolSpotService.getLatest7DaysList;
import static com.vesanieminen.froniusvisualizer.services.PriceCalculatorService.calculateSpotAveragePriceThisMonth;
import static com.vesanieminen.froniusvisualizer.services.PriceCalculatorService.getPricesToday;
import static com.vesanieminen.froniusvisualizer.services.PriceCalculatorService.getPricesTomorrow;

@Tag("bar-chart-template")
@JsModule("src/bar-chart-template.ts")
public class BarChartTemplate extends Component {

    private static final PropertyDescriptor<String, String> CHART_TITLE = PropertyDescriptors.propertyWithDefault("chartTitle", "");
    private static final PropertyDescriptor<String, String> SERIES_TITLE = PropertyDescriptors.propertyWithDefault("seriesTitle", "");
    private static final PropertyDescriptor<String, String> UNIT = PropertyDescriptors.propertyWithDefault("unit", "");
    private static final PropertyDescriptor<String, String> POST_FIX = PropertyDescriptors.propertyWithDefault("postfix", "");
    private static final PropertyDescriptor<String, String> AVERAGE_TEXT = PropertyDescriptors.propertyWithDefault("averageText", "");
    private static final PropertyDescriptor<Double, Double> AVERAGE = PropertyDescriptors.propertyWithDefault("average", -10d);
    private static final PropertyDescriptor<Integer, Integer> CURRENT_HOUR = PropertyDescriptors.propertyWithDefault("currentHour", 0);
    private static final PropertyDescriptor<String, String> LANGUAGE = PropertyDescriptors.propertyWithDefault("language", "en");

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        set(LANGUAGE, getLocale().getLanguage());
        String format = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(getLocale()).format(Utils.getCurrentLocalDateTimeHourPrecisionFinnishZone());
        set(CHART_TITLE, format);
        set(SERIES_TITLE, getTranslation("column-chart.series.title"));
        set(UNIT, getTranslation("column-chart.series.unit"));
        set(POST_FIX, getTranslation("c/kWh"));
        set(AVERAGE_TEXT, getTranslation("column-chart.month.average"));
        final var pricesToday = getPricesToday();
        var data = getLatest7DaysList();
        setNordpoolDataList(data);
        final var hour = (int) Utils.getCurrentInstantHourPrecision().getEpochSecond();
        set(CURRENT_HOUR, hour);
        var monthAverage = calculateSpotAveragePriceThisMonth();
        Utils.average(pricesToday).ifPresent(value -> set(AVERAGE, monthAverage));
    }

    public void setSeriesList(List<Double> list) {
        getElement().setPropertyList("values", list);
    }

    public void setSeriesDataList(List<Map.Entry<Instant, Double>> list) {
        getElement().setPropertyList("values", list);
    }

    public void setNordpoolDataList(List<NordpoolPrice> list) {
        getElement().setPropertyList("values", list);
    }

    @ClientCallable
    private void previous() {
        setSeriesList(getPricesToday());
    }

    @ClientCallable
    private void next() {
        setSeriesList(getPricesTomorrow());
    }

}
