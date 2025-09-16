package org.example.syncwatchservice.controller;

import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.model.Room;
import org.example.syncwatchservice.model.User;
import org.example.syncwatchservice.service.MovieService;
import org.example.syncwatchservice.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class MainController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private RoomService roomService;

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        User currentUser = getCurrentUser(session);

        List<Movie> movies = movieService.getAllMovies();
        List<Room> rooms = roomService.getAllRooms();

        Map<String, Long> roomCountsByMovie = movies.stream()
                .collect(Collectors.toMap(
                        Movie::getId,
                        movie -> roomService.getRoomsByMovieId(movie.getId()).stream().count()
                ));

        model.addAttribute("movies", movies);
        model.addAttribute("rooms", rooms);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("roomCountsByMovie", roomCountsByMovie);

        return "index";
    }

    @PostMapping("/create-room")
    public String createRoom(@RequestParam String movieId,
                             @RequestParam String roomName,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(session);

        Movie movie = movieService.getMovieById(movieId).orElse(null);
        if (movie == null) {
            redirectAttributes.addFlashAttribute("error", "Movie not found");
            return "redirect:/";
        }

        Room room = roomService.createRoom(roomName, movie, currentUser.getId());
        roomService.joinRoom(room.getId(), currentUser);

        return "redirect:/room/" + room.getId();
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
        model.addAttribute("streamUrl", movieService.getMovieStreamUrl(room.getMovie().getId()));

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

    private User getCurrentUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            user = new User(UUID.randomUUID().toString(), "Guest" + System.currentTimeMillis() % 1000);
            session.setAttribute("user", user);
        }
        return user;
    }
}