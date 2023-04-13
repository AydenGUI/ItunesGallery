package cs1302.gallery;

import java.net.http.HttpClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javafx.geometry.Pos;
import javafx.geometry.Insets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.lang.InterruptedException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cs1302.gallery.ItunesResponse;
import cs1302.gallery.ItunesResult;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    private Stage stage;
    private Scene scene;
    private HBox root;
    private VBox vbox;
    private TextField queryField;
    private Button search, playPause;
    private ImageView imgView;
    private Image[] usedImages, unusedImages;
    private Image temp;
    private int rand2;
    private ImageView[] imageViews;
    private Label searchText, headsUp;
    private ComboBox menu;
    private String menuMode;
    private ItunesResponse itunesResponse;
    private ProgressBar proBar;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new HBox(4);
        this.vbox = new VBox();
        this.search = new Button("Get Images");
        this.searchText = new Label("Search:");
        this.queryField = new TextField("");
        this.playPause = new Button("Play");
        this.menu = new ComboBox();
        this.headsUp = new Label("Type in a term, select a media type, then click the button.");

        this.menu.getItems().addAll("movie", "podcast", "music", "musicVideo", "tvShow",
                                    "software", "ebook", "all");
        this.menu.setValue("music");
        this.menuMode = "album";
        this.proBar = new ProgressBar();
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        System.out.println("init() called");
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;

        EventHandler<ActionEvent> searchEvent = (e) -> {
            search(); };
        EventHandler<ActionEvent> menuEvent = (e) -> {
            menuOption(); };

        EventHandler<ActionEvent> playEvent = (e) -> {
            onPlay(); };
        search.setOnAction(searchEvent);
        menu.setOnAction(menuEvent);
        playPause.setOnAction(playEvent);
        // Above are all "events" that occur, either from pressing a button or
        // selecting a menu option. When it occurs they each call a specified
        // method.
        HBox.setMargin(playPause, new Insets(0, 0, 0, 5)); // adds margin to the left of playPause
        HBox.setMargin(search, new Insets(0, 5, 0, 0)); // adds margin to the right of search
        this.root.getChildren().addAll(playPause, searchText
                                       ,queryField, menu, search);
        root.setAlignment(Pos.CENTER);
        for (Node child : root.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        } // sets all children of root to grow
        HBox.setHgrow(headsUp, Priority.ALWAYS);
        headsUp.setPrefHeight(50);
        this.vbox.getChildren().addAll(root, headsUp);
        defaultPictures();
        GridPane pn1 = new GridPane(), pn2 = new GridPane(),
            pn3 = new GridPane(), pn4 = new GridPane();
        for (int i = 0; i < 20; i++) {
            if (i < 5) {
                pn1.add(imageViews[i], i, 0);
            } else if (i < 10) {
                pn2.add((Node)imageViews[i], i - 5, 0);
            } else if (i < 15) {
                pn3.add((Node)imageViews[i], i - 10, 0);
            } else if (i < 20) {
                pn4.add((Node)imageViews[i], i - 15, 0);
            } // if
            // Using GridPlanes as rows with 5 images added to each "row"
            // these rows once placed can be later modified, this esscentially
            // sets areas for pictures to be placed later on.
        } // for
        vbox.getChildren().addAll(pn1, pn2, pn3, pn4);
        HBox hboox = new HBox(10);
        Label proLabel = new Label("Images Provided by iTunes Search API");
        proBar.setMaxWidth(Double.MAX_VALUE);

        hboox.getChildren().addAll(proBar, proLabel);
        hboox.setAlignment(Pos.CENTER);
        HBox.setHgrow(proBar, Priority.ALWAYS);
        HBox.setHgrow(proLabel, Priority.ALWAYS);
        //Lets children grow to fill space and centers them
        vbox.getChildren().add(hboox);
        VBox.setMargin(hboox, new Insets(10));

        this.scene = new Scene(this.vbox);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp");
        this.stage.setScene(this.scene);
        stage.setWidth(800);
        stage.setHeight(750);
        this.stage.show();
        //Sets and displays javaFX window
        Platform.runLater(() -> this.stage.setResizable(false));

    } // start

    /**
     * Action that occurs when the "Get Image" button is pressed.
     * It searches the Itunes API for results based on query and forms it into a response.
     */
    private void search() {

        playPause.setText("Play");
        playPause.setDisable(true);
        headsUp.setText("Getting images...");
        setProgress(0);
        try {
            String entity = "", limit = "&limit=200";
            String term = URLEncoder.encode(queryField.getText(), StandardCharsets.UTF_8);
            String ITUNES_API = "https://itunes.apple.com/search?term=";
            if (!menuMode.equals("all")) {
                entity = "&entity=" + menuMode;
            } // if
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ITUNES_API + term + entity + limit))
                .build();

            HttpResponse<String> response = HTTP_CLIENT
                .send(request, BodyHandlers.ofString());
            // Requests the Itunes API for a url and then
            // recieves a response which is then formatted below.

            headsUp.setText(ITUNES_API + term  + entity + limit);
            itunesResponse = GSON.fromJson(response.body(), ItunesResponse.class);
        } catch (IllegalArgumentException iae) {
            System.err.print(iae.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        } catch (InterruptedException ie) {
            System.err.println(ie.getMessage());
        } // try

        if (itunesResponse.resultCount >= 21) {
            this.useImages(itunesResponse);
        } else {
            Stage errorWindow = new Stage();
            errorWindow.setWidth(400);
            errorWindow.setHeight(400);
            errorWindow.setTitle("Insufficent Results");
            errorWindow.show();
            playPause.setDisable(false);
        } // if
    } // search

    /**
     * Action from menu tab change which modifies the search entity.
     */
    public void menuOption() {
        menuMode = menu.getValue().toString();
        if (menuMode.equals("music")) {
            this.menuMode = "album";
        } // if
        if (menuMode.equals("tvShow")) {
            this.menuMode = "tvSeason";
        } // if
    } // menu

    /**
     * Method which uses artworkUrls from the API response and places them in two arrays.
     * @param itunesResponse Response from itunes API
     */
    public void useImages(ItunesResponse itunesResponse) {
        usedImages = new Image[20];
        unusedImages = new Image[180];


        // Creates a separate thread for loading the images
        Thread loadImagesThread = new Thread(() -> {
                String type, imageUrl;
                int n;
                //synchronized (itunesResponse) {
                    n = itunesResponse.resultCount;
                    //}
                for (int i = 0; i < n; i++) {
                    synchronized (itunesResponse) {
                    imageUrl = itunesResponse.results[i].artworkUrl100;
                    type = itunesResponse.results[i].kind;
                                      }
                    if (imageUrl == null) {
                        imageUrl = "file:resources/default.png";
                    } // if
                    if ((type != null) && (type.equals("feature-movie") || type.equals("ebook") || type.equals("music-video"))) {
                        temp  = new Image(imageUrl, 160, 160, false, true);
                        //movie, ebook, and music-video image dimensions are different
                    } // if
                    else {
                       temp  = new Image(imageUrl, 200, 160
                                                , true, true, true);
                    } // else
                    final int j = i;
                    Platform.runLater(() -> setProgress((double)j / n));
                    //updates progress bar
                    if (i < 20) {
                        usedImages[i] = temp;
                    } else {
                        unusedImages[i - 20] = temp;
                    } // if
                    // Puts first 20 results in the array to be used and the
                    // rest in another array. Then using current and total results,
                    // updates the progress bar.
                } // for
                Platform.runLater(() -> {
                        setProgress(1.0);
                        setPictures();
                    });
        });
        loadImagesThread.start();



    } // useImages

    /**
     * Helper method to set the first default images.
     */
    public void defaultPictures() {
        this.imageViews = new ImageView[20];
        Image defaultImage = new Image( "file:resources/default.png", 200, 160, true, true, true);
        for (int i = 0; i < 20; i++) {
            this.imageViews[i] = new ImageView();
            this.imageViews[i].setImage(defaultImage);
        }
    } // defaultPictures

    /**
     * Changes displayed Images with the first 20 from the search.
     */
    public void setPictures() {
        for (int i = 0; i < usedImages.length; i++) {
            this.imageViews[i].setImage(usedImages[i]);
        } // for
        playPause.setDisable(false);
    } // setPictures

    /**
     * Action that occurs on playPause button press.
     * Randomly replaces a result with an unused one every second that
     * it is enabled.
     */
    public void onPlay() {

        if (playPause.getText().equals("Play") && !(unusedImages == null)) {

            playPause.setText("Pause");
        } else if (playPause.getText().equals("Pause")) {
            playPause.setText("Play");
        } // if
        Thread t = new Thread() { // Thread to allow process to not interrupt
                @Override         // the rest of the GUI.
                public void run() {
                    int  rand1 = 0, num;
                    if (!(unusedImages == null)) {
                        try {
                            num = itunesResponse.resultCount - 20;
                            while (playPause.getText().equals("Pause")) {
                                if (!(temp == null)) {
                                    imageViews[rand2].setImage(temp);
                                } //if
                                temp = null;
                                rand1 = (int)(Math.random() * num);
                                rand2 = (int)(Math.random() * 20);
                                temp = usedImages[rand2];
                                //Takes 2 random ints one out of 20, one out of the number
                                // of unused images then swaps in one of the unused images
                                // for the image in the index of the random number out of 20.
                                imageViews[rand2].setImage(unusedImages[rand1]);
                                Thread.sleep(1000);
                            } // while
                        } catch (InterruptedException ie) {
                            System.err.println(ie.getMessage());
                        } // try
                    } // if
                } // run
            };
        t.start();
    } // onPlay

    /**
     * Helper method which delays the method to avoid errors.
     * @param progress Current progress out of 1.0
     */
    private void setProgress(final double progress) {
        Platform.runLater(() -> proBar.setProgress(progress));
    } // setProgress

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        System.out.println("stop() called");
    } // stop

} // GalleryApp
