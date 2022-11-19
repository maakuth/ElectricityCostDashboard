package com.vesanieminen.froniusvisualizer.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.ChartOptions;
import com.vaadin.flow.component.charts.model.AxisType;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.DateTimeLabelFormats;
import com.vaadin.flow.component.charts.model.Labels;
import com.vaadin.flow.component.charts.model.Lang;
import com.vaadin.flow.component.charts.model.Marker;
import com.vaadin.flow.component.charts.model.PlotLine;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.RangeSelector;
import com.vaadin.flow.component.charts.model.RangeSelectorButton;
import com.vaadin.flow.component.charts.model.RangeSelectorTimespan;
import com.vaadin.flow.component.charts.model.Series;
import com.vaadin.flow.component.charts.model.SeriesTooltip;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vesanieminen.froniusvisualizer.components.DoubleLabel;
import com.vesanieminen.froniusvisualizer.components.Spacer;
import com.vesanieminen.froniusvisualizer.services.FingridService;
import com.vesanieminen.froniusvisualizer.services.NordpoolSpotService;
import com.vesanieminen.froniusvisualizer.services.model.FingridLiteResponse;
import com.vesanieminen.froniusvisualizer.services.model.FingridRealtimeResponse;
import com.vesanieminen.froniusvisualizer.services.model.NordpoolResponse;
import com.vesanieminen.froniusvisualizer.util.Utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.vesanieminen.froniusvisualizer.services.FingridService.fingridDataUpdated;
import static com.vesanieminen.froniusvisualizer.services.PriceCalculatorService.calculateSpotAveragePriceThisMonth;
import static com.vesanieminen.froniusvisualizer.services.PriceCalculatorService.calculateSpotAveragePriceThisYear;
import static com.vesanieminen.froniusvisualizer.util.Utils.convertNordpoolLocalDateTimeToFinnish;
import static com.vesanieminen.froniusvisualizer.util.Utils.decimalFormat;
import static com.vesanieminen.froniusvisualizer.util.Utils.format;
import static com.vesanieminen.froniusvisualizer.util.Utils.getCurrentTimeWithHourPrecision;
import static com.vesanieminen.froniusvisualizer.util.Utils.numberFormat;
import static com.vesanieminen.froniusvisualizer.util.Utils.utcZone;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Visualizer")
public class NordpoolspotView extends Div implements HasUrlParameter<String> {

    private final DoubleLabel priceNow;
    private final DoubleLabel lowestAndHighest;
    private final DoubleLabel averagePrice;

    private final DoubleLabel nextPrice;
    private final String fiElectricityPriceTitle;
    private final String hydroPowerProductionTitle;
    private final String windPowerProductionTitle;
    private final String nuclearPowerProductionTitle;
    private final String solarPowerProductionTitle;
    private final String consumptionTitle;
    private final String importExportTitle;
    private final String totalRenewablesTitle;
    private final String vat10 = "vat=10";
    private final String vat0 = "vat=0";
    private final double vat24Value = 1.24d;
    private final double vat10Value = 1.10d;
    private final double vat0Value = 1d;
    private double vat = vat24Value;

    private boolean isFullscreen = false;
    private boolean isTouchDevice = false;

    private final Button fullScreenButton;

    private boolean isInitialRender = true;

    private int screenWidth;

    public NordpoolspotView() {
        addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.AlignItems.CENTER, LumoUtility.TextColor.PRIMARY_CONTRAST);
        setHeightFull();

        fiElectricityPriceTitle = getTranslation("FI electricity price");
        hydroPowerProductionTitle = getTranslation("Hydro production");
        windPowerProductionTitle = getTranslation("Wind production");
        nuclearPowerProductionTitle = getTranslation("Nuclear production");
        solarPowerProductionTitle = getTranslation("Solar power");
        consumptionTitle = getTranslation("Consumption");
        importExportTitle = getTranslation("Net export - import");
        totalRenewablesTitle = getTranslation("Total renewables");

        priceNow = new DoubleLabel(getTranslation("Price now"), "");
        //priceNow.addClassNamesToSpans("color-yellow");
        lowestAndHighest = new DoubleLabel(getTranslation("Lowest / highest today"), "");
        averagePrice = new DoubleLabel(getTranslation("7 day average"), "");
        nextPrice = new DoubleLabel(getTranslation("Price in 1h"), "");

        fullScreenButton = createButton(getTranslation("Fullscreen"));
        fullScreenButton.setVisible(false);
        if (isFullscreen) {
            fullScreenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
        fullScreenButton.addClickListener(e -> {
            isFullscreen = !isFullscreen;
            fullScreenButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            if (isFullscreen) {
                fullScreenButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
            renderView();
        });
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        if (parameter != null) {
            switch (parameter) {
                case vat10 -> this.vat = vat10Value;
                case vat0 -> this.vat = vat0Value;
                default -> this.vat = vat24Value;
            }
        } else {
            this.vat = vat24Value;
        }
        if (!isInitialRender) {
            renderView();
        }
    }

    @Override
    protected void onAttach(AttachEvent e) {
        final var chart = renderView();
        e.getUI().getPage().retrieveExtendedClientDetails(details -> {
            if (details.isTouchDevice()) {
                isTouchDevice = true;
                screenWidth = details.getBodyClientWidth();
                setTouchDeviceConfiguration(chart);
            }
            fullScreenButton.setVisible(!details.isTouchDevice());
        });
        // Scroll to the top after navigation
        e.getUI().scrollIntoView();
        if ("fi".equals(e.getUI().getLocale().getLanguage())) {
            final var chartOptions = ChartOptions.get(e.getUI());
            final var lang = new Lang();
            lang.setMonths(new String[]{"Tammikuu", "Helmikuu", "Maaliskuu", "Huhtikuu", "Toukokuu", "Kesäkuu", "Heinäkuu", "Elokuu", "Syyskuu", "Lokakuu", "Marraskuu", "Joulukuu"});
            lang.setShortMonths(new String[]{"Tammi", "Helmi", "Maalis", "Huhti", "Touko", "Kesä", "Heinä", "Elo", "Syys", "Loka", "Marras", "Joulu"});
            lang.setWeekdays(new String[]{"Sunnuntai", "Maanantai", "Tiistai", "Keskiviikko", "Torstai", "Perjantai", "Lauantai"});
            chartOptions.setLang(lang);
        }
    }

    private void setTouchDeviceConfiguration(Chart chart) {
        if (isTouchDevice) {
            chart.getConfiguration().getRangeSelector().setSelected(2);
            if (screenWidth < 1000) {
                YAxis production = chart.getConfiguration().getyAxis(0);
                production.setTitle(getTranslation("Production") + " (GWh/h)");
                production.getLabels().setFormatter("return this.value/1000");
                YAxis price = chart.getConfiguration().getyAxis(1);
                price.setTitle(getTranslation("Price") + " (" + getTranslation("c/kWh") + ")");
                price.getLabels().setFormatter(null);
            }
            if (screenWidth < 600) {
                chart.getConfiguration().getRangeSelector().setInputEnabled(false);
            }
            setMobileDeviceChartHeight(chart);
            chart.drawChart(true);
        }
    }

    private void setMobileDeviceChartHeight(Chart chart) {
        setHeight("auto");
        chart.setHeight("500px");
    }

    private Chart renderView() {
        isInitialRender = false;
        NordpoolResponse nordpoolResponse = null;
        FingridRealtimeResponse fingridResponse = null;
        List<FingridLiteResponse> windEstimateResponses = null;
        List<FingridLiteResponse> productionEstimateResponses = null;
        List<FingridLiteResponse> consumptionEstimateResponses = null;
        try {
            // the TVO OL3 requires some page crawling to work reliably
            //var test = getDayAheadPrediction();
            nordpoolResponse = NordpoolSpotService.getLatest7Days();
            fingridResponse = FingridService.getLatest7Days();
            windEstimateResponses = FingridService.getWindEstimate();
            productionEstimateResponses = FingridService.getProductionEstimate();
            consumptionEstimateResponses = FingridService.getConsumptionEstimate();
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        removeAll();
        createMenuLayout();
        var pricesLayout = new Div(priceNow, nextPrice, lowestAndHighest, averagePrice);
        try {
            final var averagePriceThisMonth = calculateSpotAveragePriceThisMonth();
            pricesLayout.add(new DoubleLabel(getTranslation("Average this month"), decimalFormat.format(averagePriceThisMonth) + " " + getTranslation("c/kWh")));
            final var averagePriceThisYear = calculateSpotAveragePriceThisYear();
            pricesLayout.add(new DoubleLabel(getTranslation("Average this year"), decimalFormat.format(averagePriceThisYear) + " " + getTranslation("c/kWh")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        pricesLayout.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexWrap.WRAP, LumoUtility.Width.FULL);
        add(pricesLayout);

        if (nordpoolResponse != null && nordpoolResponse.isValid()) {
            final var spotDataUpdatedTime = convertNordpoolLocalDateTimeToFinnish(nordpoolResponse.data.DateUpdated);
            final var spotDataUpdated = format(spotDataUpdatedTime, getLocale());
            final var spotDataUpdatedSpan = new Span(getTranslation("price.data.updated") + ": " + spotDataUpdated + ", ");
            spotDataUpdatedSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

            final var fingridDataUpdatedFormatted = format(fingridDataUpdated, getLocale());
            final var fingridDataUpdatedSpan = new Span(getTranslation("fingrid.data.updated") + ": " + fingridDataUpdatedFormatted);
            fingridDataUpdatedSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            final var div = new Div(spotDataUpdatedSpan, fingridDataUpdatedSpan);
            div.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexWrap.WRAP, LumoUtility.Gap.Column.XSMALL, LumoUtility.Margin.Horizontal.MEDIUM, LumoUtility.JustifyContent.CENTER);
            add(div);
        }

        var chart = new Chart(ChartType.LINE);
        // Buggy still and cannot be enabled yet:
        //chart.getConfiguration().setExporting(true);
        //final var exporting = chart.getConfiguration().getExporting();
        //exporting.setScale(1);
        //exporting.setSourceHeight(500);
        //exporting.setSourceWidth(1320);
        //exporting.setAllowHTML(true);
        chart.setTimeline(true);
        chart.getConfiguration().getLegend().setEnabled(true);
        chart.getConfiguration().getChart().setStyledMode(true);
        if (isFullscreen) {
            chart.setHeightFull();
        } else {
            chart.setHeight("580px");
            chart.setMaxWidth("1320px");
        }

        // create x and y-axis
        createXAxis(chart);
        createFingridYAxis(chart);
        createSpotPriceYAxis(chart);

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        if (fingridResponse != null) {
            final var hydroPowerSeries = createDataSeries(fingridResponse.HydroPower, hydroPowerProductionTitle);
            final var windPowerSeries = createDataSeries(fingridResponse.WindPower, windPowerProductionTitle);
            final var nuclearPowerSeries = createDataSeries(fingridResponse.NuclearPower, nuclearPowerProductionTitle);
            final var solarPowerSeries = createDataSeries(fingridResponse.SolarPower, solarPowerProductionTitle);
            final var consumptionSeries = createDataSeries(fingridResponse.Consumption, consumptionTitle);
            final var importExportSeries = createDataSeries(fingridResponse.NetImportExport, importExportTitle);
            final var renewablesSeries = createRenewablesDataSeries(fingridResponse);
            final var windEstimateDataSeries = createEstimateDataSeries(windEstimateResponses, getTranslation("Wind production estimate"));
            final var productionEstimateDataSeries = createEstimateDataSeries(productionEstimateResponses, getTranslation("Production estimate"));
            productionEstimateDataSeries.setVisible(false);
            final var consumptionEstimateDataSeries = createEstimateDataSeries(consumptionEstimateResponses, getTranslation("Consumption estimate"));
            consumptionEstimateDataSeries.setVisible(false);
            final var spotPriceDataSeries = createSpotPriceDataSeries(nordpoolResponse, chart, dateTimeFormatter, new ArrayList<>(Arrays.asList(hydroPowerSeries, windPowerSeries, nuclearPowerSeries, solarPowerSeries, consumptionSeries, importExportSeries, windEstimateDataSeries, consumptionEstimateDataSeries, productionEstimateDataSeries, renewablesSeries)));
            configureChartTooltips(chart, hydroPowerSeries, windPowerSeries, nuclearPowerSeries, solarPowerSeries, consumptionSeries, importExportSeries, spotPriceDataSeries, renewablesSeries);
            //setNetToday(fingridResponse, df, netToday);
        } else {
            add(new Span(getTranslation("Fingrid API is down currently ;~(")));
            final var spotPriceDataSeries = createSpotPriceDataSeries(nordpoolResponse, chart, dateTimeFormatter, new ArrayList<>());
            configureChartTooltips(chart, null, null, null, null, null, null, spotPriceDataSeries, null);
        }

        final var rangeSelector = new RangeSelector();
        rangeSelector.setButtons(
                new RangeSelectorButton(RangeSelectorTimespan.DAY, 1, getTranslation("1d")),
                new RangeSelectorButton(RangeSelectorTimespan.DAY, 2, getTranslation("2d")),
                new RangeSelectorButton(RangeSelectorTimespan.DAY, 3, getTranslation("3d")),
                new RangeSelectorButton(RangeSelectorTimespan.DAY, 5, getTranslation("5d")),
                new RangeSelectorButton(RangeSelectorTimespan.ALL, getTranslation("7d"))
        );
        rangeSelector.setButtonSpacing(12);
        rangeSelector.setSelected(isTouchDevice ? 2 : 4);
        chart.getConfiguration().setRangeSelector(rangeSelector);
        rangeSelector.setEnabled(true);
        rangeSelector.setInputEnabled(true);

        // TODO: bring back the average price per day?
        //final var averageValue = mapToPrice(format, nordpoolResponse.data.Rows.get(26));
        //PlotLine averagePrice = new PlotLine();
        //averagePrice.setLabel(new Label("Average price: " + averageValue + " c/kWh"));
        //averagePrice.setValue(averageValue);
        //chart.getConfiguration().getyAxis().addPlotLine(averagePrice);

        setTouchDeviceConfiguration(chart);

        add(chart);
        add(new Spacer());
        final Span fingridFooter = createFingridLicenseSpan();
        add(fingridFooter);
        return chart;
    }

    private Span createFingridLicenseSpan() {
        final var fingridLink = new Anchor("http://data.fingrid.fi", "data.fingrid.fi");
        final var fingridCCLicenseLink = new Anchor("https://creativecommons.org/licenses/by/4.0/", "CC 4.0 B");
        final var fingridSourceSpan = new Span(getTranslation("fingrid.source"));
        final var fingridMainLink = new Anchor("http://fingrid.fi", "Fingrid");
        final var licenseSpan = new Span(getTranslation("fingrid.license"));
        final var fingridFooter = new Span(fingridSourceSpan, fingridMainLink, new Span(" / "), fingridLink, new Span(" / "), licenseSpan, fingridCCLicenseLink);
        fingridFooter.addClassNames(LumoUtility.Display.FLEX, LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL, LumoUtility.Margin.Bottom.XSMALL, LumoUtility.Gap.XSMALL);
        return fingridFooter;
    }

    private void createFingridYAxis(Chart chart) {
        final var fingridYAxis = new YAxis();
        var labelsFingrid = new Labels();
        labelsFingrid.setFormatter("return this.value +' MWh/h'");
        fingridYAxis.setLabels(labelsFingrid);
        fingridYAxis.setTitle(getTranslation("Production"));
        fingridYAxis.setOpposite(false);
        chart.getConfiguration().addyAxis(fingridYAxis);
    }

    private void createSpotPriceYAxis(Chart chart) {
        final var yAxisSpot = new YAxis();
        var labels = new Labels();
        labels.setReserveSpace(true);
        labels.setFormatter("return this.value +' c/kWh'");
        yAxisSpot.setLabels(labels);
        yAxisSpot.setMin(0);
        //yAxisSpot.setMinRange(-1);
        yAxisSpot.setTitle(getTranslation("Price"));
        yAxisSpot.setOpposite(true);
        chart.getConfiguration().addyAxis(yAxisSpot);
    }

    private void createXAxis(Chart chart) {
        final var xAxis = new XAxis();
        xAxis.setTitle(getTranslation("Time"));
        xAxis.setType(AxisType.DATETIME);
        chart.getConfiguration().addxAxis(xAxis);
    }

    private void configureChartTooltips(Chart chart, DataSeries hydroPowerSeries, DataSeries windPowerSeries, DataSeries nuclearPowerSeries, DataSeries solarPowerSeries, DataSeries consumptionSeries, DataSeries importExportSeries, DataSeries spotPriceDataSeries, DataSeries renewablesSeries) {
        final var plotOptionsLineSpot = new PlotOptionsLine();
        plotOptionsLineSpot.setStickyTracking(true);
        plotOptionsLineSpot.setMarker(new Marker(false));
        final var seriesTooltipSpot = new SeriesTooltip();
        seriesTooltipSpot.setValueDecimals(2);
        seriesTooltipSpot.setValueSuffix(" " + getTranslation("c/kWh"));
        final var dateTimeLabelFormats = new DateTimeLabelFormats();
        seriesTooltipSpot.setDateTimeLabelFormats(dateTimeLabelFormats);
        plotOptionsLineSpot.setTooltip(seriesTooltipSpot);
        spotPriceDataSeries.setPlotOptions(plotOptionsLineSpot);

        final var plotOptionsLine = new PlotOptionsLine();
        plotOptionsLine.setStickyTracking(true);
        plotOptionsLine.setMarker(new Marker(false));
        chart.getConfiguration().setPlotOptions(plotOptionsLine);
        final var tooltip = new Tooltip();
        tooltip.setValueDecimals(0);
        tooltip.setValueSuffix(" MWh/h");
        chart.getConfiguration().setTooltip(tooltip);

        if (hydroPowerSeries != null) {
            // Change the fingrid series to use the 2nd y-axis
            hydroPowerSeries.setVisible(false);
            windPowerSeries.setVisible(true);
            nuclearPowerSeries.setVisible(false);
            solarPowerSeries.setVisible(false);
            consumptionSeries.setVisible(false);
            importExportSeries.setVisible(false);
            renewablesSeries.setVisible(false);
        }
        spotPriceDataSeries.setyAxis(1);

        // Add plotline to point the current time:
        PlotLine plotLine = new PlotLine();
        plotLine.setClassName("time");
        final LocalDateTime nowWithHourOnly = getCurrentTimeWithHourPrecision();
        plotLine.setValue(nowWithHourOnly.toEpochSecond(ZoneOffset.UTC) * 1000);
        chart.getConfiguration().getxAxis().addPlotLine(plotLine);
    }

    private DataSeries createDataSeries(List<FingridRealtimeResponse.Data> datasource, String title) {
        final var dataSeries = new DataSeries(title);
        for (FingridRealtimeResponse.Data data : datasource) {
            final var dataSeriesItem = new DataSeriesItem();
            dataSeriesItem.setX(data.start_time.plusHours(3).withMinute(0).toInstant());
            dataSeriesItem.setY(data.value);
            dataSeries.add(dataSeriesItem);
        }
        return dataSeries;
    }

    private DataSeries createRenewablesDataSeries(FingridRealtimeResponse fingridResponse) {
        final var dataSeries = new DataSeries(totalRenewablesTitle);
        for (int i = 0; i < fingridResponse.WindPower.size() && i < fingridResponse.HydroPower.size() && i < fingridResponse.SolarPower.size(); ++i) {
            final var value = fingridResponse.WindPower.get(i).value + fingridResponse.HydroPower.get(i).value + fingridResponse.SolarPower.get(i).value;
            final var dataSeriesItem = new DataSeriesItem();
            dataSeriesItem.setX(fingridResponse.WindPower.get(i).start_time.withMinute(0).plusHours(3).toInstant());
            dataSeriesItem.setY(value);
            dataSeries.add(dataSeriesItem);
        }
        return dataSeries;
    }

    private DataSeries createEstimateDataSeries(List<FingridLiteResponse> dataSource, String title) {
        final var dataSeries = new DataSeries(title);
        for (FingridLiteResponse response : dataSource) {
            final var dataSeriesItem = new DataSeriesItem();
            dataSeriesItem.setX(response.start_time.toInstant().plus(Duration.ofHours(3)));
            dataSeriesItem.setY(response.value);
            dataSeries.add(dataSeriesItem);
        }
        return dataSeries;
    }

    public void setNetToday(FingridRealtimeResponse fingridResponse, DecimalFormat df, DoubleLabel netToday) {
        final var now = getCurrentTimeWithHourPrecision();
        final var value = fingridResponse.NetImportExport.stream().filter(item -> item.start_time.getDayOfMonth() == now.getDayOfMonth()).map(item -> item.value).reduce(0d, Double::sum);
        final var formattedValue = df.format(value) + " MWh/h";
        netToday.setTitleBottom(formattedValue);
        // The red color isn't looking good yet:
        //if (value < 0) {
        //    netToday.getSpanBottom().addClassNames("color-red");
        //}
        //else {
        //    netToday.getSpanBottom().addClassNames("color-red");
        //}
    }

    public enum VAT {
        VAT24("VAT 24%"),
        VAT10("VAT 10%"),
        VAT0("VAT 0%");

        private String vatName;

        VAT(String vatName) {
            this.vatName = vatName;
        }

        public String getVatName() {
            return vatName;
        }
    }

    private void createMenuLayout() {
        final var vatComboBox = new ComboBox<VAT>();
        vatComboBox.addClassNames(LumoUtility.Padding.NONE);
        vatComboBox.setMinWidth(8, Unit.EM);
        vatComboBox.setWidthFull();
        vatComboBox.setItems(VAT.values());
        if (vat == vat24Value) {
            vatComboBox.setValue(VAT.VAT24);
        }
        if (vat == vat10Value) {
            vatComboBox.setValue(VAT.VAT10);
        }
        if (vat == vat0Value) {
            vatComboBox.setValue(VAT.VAT0);
        }
        vatComboBox.setItemLabelGenerator(item -> getTranslation(item.getVatName()));
        final var priceListButton = createButton(getTranslation("List"));
        final var priceCalculationButton = createButton(getTranslation("Calculator"));
        final var menuLayout = new Div(vatComboBox, priceListButton, priceCalculationButton, fullScreenButton);
        menuLayout.addClassNames(LumoUtility.Display.FLEX, LumoUtility.Width.FULL);
        add(menuLayout);

        // Add event listeners
        vatComboBox.addValueChangeListener(e -> getUI().ifPresent(ui -> {
            switch (e.getValue()) {
                case VAT24 -> ui.navigate(NordpoolspotView.class);
                case VAT10 -> ui.navigate(NordpoolspotView.class, vat10);
                case VAT0 -> ui.navigate(NordpoolspotView.class, vat0);
            }
        }));
        priceListButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(PriceListView.class)));
        priceCalculationButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(PriceCalculatorView.class)));
    }

    private static Button createButton(String text) {
        Button button = new Button(text);
        button.setWidthFull();
        button.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.BorderRadius.NONE, LumoUtility.Margin.Vertical.NONE, LumoUtility.Height.MEDIUM, LumoUtility.BorderColor.CONTRAST_10, LumoUtility.Border.ALL);
        return button;
    }

    private DataSeries createSpotPriceDataSeries(NordpoolResponse nordpoolResponse, Chart chart, DateTimeFormatter dateTimeFormatter, ArrayList<Series> series) {
        var now = getCurrentTimeWithHourPrecision();
        var highest = Double.MIN_VALUE;
        var lowest = Double.MAX_VALUE;
        var total = 0d;
        var amount = 0;
        final var dataSeries = new DataSeries(fiElectricityPriceTitle);
        final var rows = nordpoolResponse.data.Rows;
        int columnIndex = 6;
        while (columnIndex >= 0) {
            for (NordpoolResponse.Row row : rows.subList(0, rows.size() - 6)) {
                final var dataSeriesItem = new DataSeriesItem();
                final var time = row.StartTime.toString().split("T")[1];
                NordpoolResponse.Column column = row.Columns.get(columnIndex);
                final var dateTimeString = column.Name + " " + time;
                final var dataLocalDataTime = LocalDateTime.parse(dateTimeString, dateTimeFormatter);
                final var instant = dataLocalDataTime.toInstant(ZoneOffset.of("-01:00"));
                final var localDateTime = LocalDateTime.ofInstant(instant, utcZone);
                dataSeriesItem.setX(instant);
                try {
                    final var y = numberFormat.parse(column.Value).doubleValue() * vat / 10;
                    total += y;
                    ++amount;
                    dataSeriesItem.setY(y);
                    if (Objects.equals(localDateTime, now)) {
                        priceNow.setTitleBottom(Utils.decimalFormat.format(y) + " " + getTranslation("c/kWh"));
                    }
                    if (Objects.equals(localDateTime, now.plusHours(1))) {
                        nextPrice.setTitleBottom(Utils.decimalFormat.format(y) + " " + getTranslation("c/kWh"));
                    }
                    if (localDateTime.getDayOfMonth() == now.getDayOfMonth()) {
                        if (y > highest) {
                            highest = y;
                        }
                        if (y < lowest) {
                            lowest = y;
                        }
                    }
                } catch (ParseException e) {
                    // skip when the time is "-": changing from or to summer time.
                    continue;
                }
                dataSeries.add(dataSeriesItem);
            }
            --columnIndex;
        }
        lowestAndHighest.setTitleBottom(Utils.decimalFormat.format(lowest) + " / " + Utils.decimalFormat.format(highest) + " " + getTranslation("c/kWh"));
        averagePrice.setTitleBottom(Utils.decimalFormat.format(total / amount) + " " + getTranslation("c/kWh"));
        series.add(0, dataSeries);
        chart.getConfiguration().setSeries(series);
        return dataSeries;
    }

}
