package swe4.client.betapplication.controllers;

import javafx.beans.Observable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import swe4.client.services.DataService;
import swe4.client.services.ServiceFactory;
import swe4.client.services.StateService;
import swe4.client.utils.TableDateCell;
import swe4.domain.entities.*;
import swe4.server.services.BetService;

import java.net.URL;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

public class BetViewController implements Initializable {


    @FXML
    private TableView<Game> gameTable;
    @FXML
    private TableColumn<Game, String> statusCol;
    @FXML
    private TableColumn<Game, LocalDateTime> startCol;
    @FXML
    private TableColumn<Game, LocalDateTime> endCol;
    @FXML
    private TableColumn<Game, String> tippedWinnerCol;
    @FXML
    private TableColumn<Game, String> placedCol;

    @FXML
    private ComboBox<Team> winnerTeamField;
    @FXML
    private Button placeBetBtn;

    private final BetService betService = ServiceFactory.betServiceInstance();
    private final StateService stateService = StateService.getInstance();
    private final DataService dataService = ServiceFactory.dataServiceInstance();
    private final ObservableList<Team> opposingTeams = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus(LocalDateTime.now())));
        startCol.setCellFactory(cell -> new TableDateCell<>());
        endCol.setCellFactory(cell -> new TableDateCell<>());
        tippedWinnerCol.setCellValueFactory(this::getBetWinner);
        placedCol.setCellValueFactory(this::getPlacementTime);

        gameTable.setItems(dataService.games());
        gameTable.getSelectionModel()
                .selectedItemProperty()
                .addListener(this::selectionChanged);
        winnerTeamField.setItems(opposingTeams);

        winnerTeamField.getSelectionModel()
                .selectedItemProperty()
                .addListener(this::winnerTeamSelectionChanged);
        gameTable.getSortOrder().add(startCol);
    }

    public void placeBet(ActionEvent actionEvent) {
        final Game selectedGame = gameTable.getSelectionModel().getSelectedItem();
        final Team selectedTeam = winnerTeamField.getValue();
        final User user = stateService.getCurrentUser();
        final Bet betByUser = getBetByUserAndGame(selectedGame, user);
        PlacementTime placementTime = PlacementTime.BEFORE;
        if (selectedGame.getStartTime().isBefore(LocalDateTime.now())) {
            placementTime = PlacementTime.DURING;
        }
        if (betByUser == null) {
            final Bet newBet = new Bet(user, selectedGame, selectedTeam, placementTime);
            try {
                betService.insertBet(newBet);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            betByUser.setWinner(selectedTeam);
            betByUser.setPlaced(placementTime);
            try {
                betService.updateBet(betByUser);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        dataService.refreshGames();
    }

    private void selectionChanged(Observable observable) {
        final Game selectedGame = gameTable.getSelectionModel().getSelectedItem();
        if (selectedGame != null) {
            User user = stateService.getCurrentUser();
            final Bet betByUser = getBetByUserAndGame(selectedGame, user);
            opposingTeams.setAll(selectedGame.getTeam1(), selectedGame.getTeam2());
            if (betByUser != null) {
                winnerTeamField.getSelectionModel().select(betByUser.getWinner());
                placeBetBtn.setText("Update Bet");
            } else {
                winnerTeamField.getSelectionModel().selectFirst();
                placeBetBtn.setText("Place Bet");
            }
        }
    }

    private void winnerTeamSelectionChanged(Observable observable) {
        placeBetBtn.setDisable(
                winnerTeamField.getSelectionModel().getSelectedItem() == null
                        || gameTable.getSelectionModel()
                        .getSelectedItem()
                        .getEstimatedEndTime()
                        .isBefore(LocalDateTime.now())
        );
    }

    private SimpleStringProperty getBetWinner(TableColumn.CellDataFeatures<Game, String> cell) {
        String name = "";
        Bet optBet = getBetByUserAndGame(cell.getValue(), stateService.getCurrentUser());
        if (optBet != null) {
            name = optBet.getWinner().toString();
        }
        return new SimpleStringProperty(name);
    }

    private SimpleStringProperty getPlacementTime(TableColumn.CellDataFeatures<Game, String> cell) {
        String name = "";
        Bet optBet = getBetByUserAndGame(cell.getValue(), stateService.getCurrentUser());
        if (optBet != null) {
            name = optBet.getPlaced().name();
        }
        return new SimpleStringProperty(name);
    }

    private Bet getBetByUserAndGame(Game selectedGame, User user) {
        try {
            return betService.findBetByUserAndGame(user, selectedGame);
        } catch (RemoteException e) {
            return null;
        }
    }
}
