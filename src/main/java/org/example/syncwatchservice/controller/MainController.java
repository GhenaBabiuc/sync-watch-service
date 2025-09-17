package org.example.syncwatchservice.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.syncwatchservice.model.Episode;
import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.model.Room;
import org.example.syncwatchservice.model.Season;
import org.example.syncwatchservice.model.Series;
import org.example.syncwatchservice.model.User;
import org.example.syncwatchservice.service.MovieService;
import org.example.syncwatchservice.service.RoomService;
import org.example.syncwatchservice.service.SeriesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final MovieService movieService;
    private final SeriesService seriesService;
    private final RoomService roomService;

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        User currentUser = getCurrentUser(session);

        List<Movie> movies = movieService.getAllMovies();
        List<Series> series = seriesService.getAllSeries();
        List<Room> rooms = roomService.getAllRooms();

        Map<String, Long> movieRoomCounts = movies.stream()
                .collect(Collectors.toMap(
                        movie -> "movie_" + movie.getId(),
                        movie -> roomService.getRoomsByMovieId(movie.getId().toString()).stream().count()
                ));

        Map<String, Long> seriesRoomCounts = series.stream()
                .collect(Collectors.toMap(
                        s -> "series_" + s.getId(),
                        s -> roomService.getRoomsBySeriesId(s.getId()).stream().count()
                ));

        model.addAttribute("movies", movies);
        model.addAttribute("series", series);
        model.addAttribute("rooms", rooms);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("movieRoomCounts", movieRoomCounts);
        model.addAttribute("seriesRoomCounts", seriesRoomCounts);

        return "index";
    }

    @PostMapping("/create-movie-room")
    public String createMovieRoom(@RequestParam Long movieId,
                                  @RequestParam String roomName,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(session);

        try {
            Room room = roomService.createMovieRoom(roomName, movieId, currentUser.getId());
            roomService.joinRoom(room.getId(), currentUser);
            return "redirect:/room/" + room.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/create-series-room")
    public String createSeriesRoom(@RequestParam Long seriesId,
                                   @RequestParam String roomName,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(session);

        try {
            Room room = roomService.createSeriesRoom(roomName, seriesId, currentUser.getId());
            roomService.joinRoom(room.getId(), currentUser);
            return "redirect:/room/" + room.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/room/{roomId}")
    public String room(@PathVariable String roomId, Model model, HttpSession session) {
        User currentUser = getCurrentUser(session);

        Room room = roomService.getRoomById(roomId).orElse(null);
        if (room == null) {
            model.addAttribute("error", "Room not found");
            return "error";
        }

        if (!room.getUsers().contains(currentUser)) {
            roomService.joinRoom(roomId, currentUser);
        }

        model.addAttribute("room", room);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isHost", roomService.isHost(roomId, currentUser.getId()));
        model.addAttribute("streamUrl", room.getStreamUrl());

        if (room.getRoomType() == Room.RoomType.SERIES) {
            List<Episode> availableEpisodes = roomService.getAvailableEpisodes(roomId);
            List<Season> seasons = seriesService.getSeasonsBySeries(room.getSeries().getId());

            model.addAttribute("availableEpisodes", availableEpisodes);
            model.addAttribute("seasons", seasons);
            model.addAttribute("currentEpisode", room.getCurrentEpisode());
        }

        return "room";
    }

    @PostMapping("/join-room/{roomId}")
    public String joinRoom(@PathVariable String roomId, HttpSession session) {
        User currentUser = getCurrentUser(session);

        if (!roomService.joinRoom(roomId, currentUser)) {
            return "redirect:/?error=room-not-found";
        }

        return "redirect:/room/" + roomId;
    }

    @PostMapping("/leave-room/{roomId}")
    public String leaveRoom(@PathVariable String roomId, HttpSession session) {
        User currentUser = getCurrentUser(session);
        roomService.leaveRoom(roomId, currentUser.getId());
        return "redirect:/";
    }

    @PostMapping("/room/{roomId}/switch-episode")
    @ResponseBody
    public Map<String, Object> switchEpisode(@PathVariable String roomId,
                                             @RequestParam Long episodeId,
                                             HttpSession session) {
        User currentUser = getCurrentUser(session);
        boolean success = roomService.switchEpisode(roomId, episodeId, currentUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null && room.getCurrentEpisode() != null) {
                response.put("episode", room.getCurrentEpisode());
                response.put("streamUrl", room.getStreamUrl());
            }
        }

        return response;
    }

    @PostMapping("/room/{roomId}/next-episode")
    @ResponseBody
    public Map<String, Object> nextEpisode(@PathVariable String roomId, HttpSession session) {
        User currentUser = getCurrentUser(session);
        boolean success = roomService.switchToNextEpisode(roomId, currentUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null && room.getCurrentEpisode() != null) {
                response.put("episode", room.getCurrentEpisode());
                response.put("streamUrl", room.getStreamUrl());
            }
        }

        return response;
    }

    @PostMapping("/room/{roomId}/previous-episode")
    @ResponseBody
    public Map<String, Object> previousEpisode(@PathVariable String roomId, HttpSession session) {
        User currentUser = getCurrentUser(session);
        boolean success = roomService.switchToPreviousEpisode(roomId, currentUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            Room room = roomService.getRoomById(roomId).orElse(null);
            if (room != null && room.getCurrentEpisode() != null) {
                response.put("episode", room.getCurrentEpisode());
                response.put("streamUrl", room.getStreamUrl());
            }
        }

        return response;
    }

    @GetMapping("/set-username")
    public String setUsernameForm(Model model, HttpSession session) {
        User currentUser = getCurrentUser(session);
        model.addAttribute("currentUser", currentUser);
        return "set-username";
    }

    @PostMapping("/set-username")
    public String setUsername(@RequestParam String username, HttpSession session) {
        User currentUser = getCurrentUser(session);
        currentUser.setUsername(username);
        session.setAttribute("user", currentUser);
        return "redirect:/";
    }

    @PostMapping("/create-room")
    public String createRoom(@RequestParam(required = false) String movieId,
                             @RequestParam(required = false) Long movieIdLong,
                             @RequestParam String roomName,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        Long actualMovieId = movieIdLong;
        if (actualMovieId == null && movieId != null) {
            try {
                actualMovieId = Long.parseLong(movieId);
            } catch (NumberFormatException e) {
                redirectAttributes.addFlashAttribute("error", "Invalid movie ID");
                return "redirect:/";
            }
        }

        if (actualMovieId == null) {
            redirectAttributes.addFlashAttribute("error", "Movie ID is required");
            return "redirect:/";
        }

        return createMovieRoom(actualMovieId, roomName, session, redirectAttributes);
    }

    private User getCurrentUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            user = new User(UUID.randomUUID().toString(), "Guest" + System.currentTimeMillis() % 1000);
            session.setAttribute("user", user);
        }
        return user;
    }
}
