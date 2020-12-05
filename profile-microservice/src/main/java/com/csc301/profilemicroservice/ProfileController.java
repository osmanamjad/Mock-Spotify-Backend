package com.csc301.profilemicroservice;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.csc301.profilemicroservice.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class ProfileController {
	public static final String KEY_USER_NAME = "userName";
	public static final String KEY_USER_FULLNAME = "fullName";
	public static final String KEY_USER_PASSWORD = "password";

	@Autowired
	private final ProfileDriverImpl profileDriver;

	@Autowired
	private final PlaylistDriverImpl playlistDriver;

	OkHttpClient client = new OkHttpClient();

	public ProfileController(ProfileDriverImpl profileDriver, PlaylistDriverImpl playlistDriver) {
		this.profileDriver = profileDriver;
		this.playlistDriver = playlistDriver;
	}

	/**
	 * Adds a profile to the profile database by calling profileDriver createUserProfile, using the given parameters as profile information.
	 * And for each profile created, also create a relation: (nProfile:profile)-[:created]->(nPlaylist:playlist)
	 * 
	 * @param params				the map of parameters, which will contain username, fullname, and password
	 * @param request				to determine path that was called from within each REST route
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/profile", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addProfile(@RequestParam Map<String, String> params,
			HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;
		
		if (params.get(KEY_USER_NAME) == null || params.get(KEY_USER_FULLNAME ) == null || 
				params.get(KEY_USER_PASSWORD) == null) {
			dbQueryStatus = new DbQueryStatus("Missing query params", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		else {
			dbQueryStatus = profileDriver.createUserProfile(params.get(KEY_USER_NAME), 
					params.get(KEY_USER_FULLNAME), params.get(KEY_USER_PASSWORD));
		}
		
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
	
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	/**
	 * Adds a relation "follows" from user with userName to user with friendUserName by calling profileDriver function followFriend.
	 * The relations has the form: (profile)-[:follows]->(:profile)
	 * 
	 * @param userName				the user that wants to follow
	 * @param friendUserName		the user to be followed
	 * @param request				to determine path that was called from within each REST route
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/followFriend/{userName}/{friendUserName}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> followFriend(@PathVariable("userName") String userName,
			@PathVariable("friendUserName") String friendUserName, HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		
		dbQueryStatus = profileDriver.followFriend(userName, friendUserName);
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}

	/**
	 * Retrieve the song names of all the songs that the specified user's friends have liked, 
	 * by calling local method getFriendsSongIdsAndSongTitles. This method calls profileDriver function getAllSongFriendsLike,
	 * which queries the neo4j database and returns a map of the songs and their favourite songIds.
	 * The getFriendsSongIdsAndSongTitles then creates a map from friend->songIds to friend->songTitles
	 * 
	 * @param userName				for the user that is specified, find this persons friends favorite songs
	 * @param request				to determine path that was called from within each REST route
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/getAllFriendFavouriteSongTitles/{userName}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getAllFriendFavouriteSongTitles(@PathVariable("userName") String userName,
			HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));
		
		dbQueryStatus = getFriendsSongIdsAndSongTitles(userName);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	/**
	 * Removes a relation "follows" from user with userName to user with friendUserName, by calling profileDriver function unfollowFriend.
	 * The relations has the form: (profile)-[:follows]->(:profile)
	 * 
	 * @param userName 				the user that wants to unfollow
	 * @param friendUserName 		the user to be unfollowed
	 * @param request				the response data, status, message and path from the called request
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/unfollowFriend/{userName}/{friendUserName}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> unfollowFriend(@PathVariable("userName") String userName,
			@PathVariable("friendUserName") String friendUserName, HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		
		dbQueryStatus = profileDriver.unfollowFriend(userName, friendUserName);
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	/**
	 * Allows a Profile to like a song and add it to their favorites.
	 * We call local function likeSongAndUpdateFavourites, which makes requests (/getSongById, /updateSongFavouritesCount)
	 * to the song microservice to do the heavy lifting.
	 * 
	 * @param userName				the userName of the Profile that is going to like the song
	 * @param songId				the String that represents the _id of the song that needs to be liked
	 * @param request				the response data, status, message and path from the called request
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/likeSong/{userName}/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> likeSong(@PathVariable("userName") String userName,
			@PathVariable("songId") String songId, HttpServletRequest request) {
				
		Map<String, Object> response = new HashMap<String, Object>();
		DbQueryStatus dbQueryStatus = likeSongAndUpdateFavourites(userName, songId, "false");

		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	/**
	 * Allows a Profile to unlike a song and remove it from their favorites.
	 * We call local function likeSongAndUpdateFavourites, which makes requests (/getSongById, /updateSongFavouritesCount)
	 * o the song microservice to do the heavy lifting.
	 * 
	 * @param userName 				the userName of the Profile that is going to unlike the song
	 * @param songId 				the String that represents the _id of the song that needs to be unliked
	 * @param request				the response data, status, message and path from the called request
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/unlikeSong/{userName}/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> unlikeSong(@PathVariable("userName") String userName,
			@PathVariable("songId") String songId, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		DbQueryStatus dbQueryStatus = likeSongAndUpdateFavourites(userName, songId, "true");

		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	/**
	 * Deletes the specified songId from the neo4j database and removes it from all playlist relations, 
	 * calls the playlistDriver function deleteSongFromDb.
	 * 
	 * @param songId				the String that represents the _id of the song that needs to be deleted
	 * @param request 				the response data, status, message and path from the called request
	 * @return Map<String, Object>	the response data, status, message and path from the called request
	 */
	@RequestMapping(value = "/deleteAllSongsFromDb/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> deleteAllSongsFromDb(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		
		DbQueryStatus dbQueryStatus = playlistDriver.deleteSongFromDb(songId);
		
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}
	
	/**
	 * Given a profile with the specified userName, contact the song microservice routes to get song information.
	 * Here we call like/unlikeSong to add/remove the song from a profiles playlist and 
	 * update the songs favorite count in the mongo database accordingly.
	 * 
	 * @param userName			the userName of the specified Profile 
	 * @param songId			the String that represents the _id of the specified song
	 * @param shouldDecrement	the boolean representing whether or not to decrement
	 * @return DbQueryStatus 	the message, status, and result of running database query 
	 */
	public DbQueryStatus likeSongAndUpdateFavourites(String userName, String songId, String shouldDecrement) {
		DbQueryStatus dbQueryStatus = null;

		try {
			HttpUrl.Builder getSongUrlBuilder = HttpUrl.parse("http://localhost:3001" + "/getSongById").newBuilder();
			getSongUrlBuilder.addPathSegment(songId);
			String getSongUrl = getSongUrlBuilder.build().toString();

			Request getSongReq = new Request.Builder()
					.url(getSongUrl)
					.method("GET", null)
					.build();

			Call getSongCall = client.newCall(getSongReq);
			Response responseFromGetSong = null;

			String getSongBody = "{}";

			try {
				responseFromGetSong = getSongCall.execute();
				getSongBody = responseFromGetSong.body().string();
				
				// if /getSongById found a song successfully, then we attempt to add/remove it to playlist
				if (getSongBody.contains("\"status\":\"OK\"")) {
					if (shouldDecrement == "false") {
						dbQueryStatus = playlistDriver.likeSong(userName, songId);
					} else {
						dbQueryStatus = playlistDriver.unlikeSong(userName, songId);
					}
					
					//if it was successfully liked/unliked then update its favourites count
					if (dbQueryStatus.getMessage().contains("Successfully")) {
						try {
							HttpUrl.Builder favouritesUrlBuilder = HttpUrl.parse("http://localhost:3001" + "/updateSongFavouritesCount").newBuilder();
							favouritesUrlBuilder.addPathSegment(songId);
							favouritesUrlBuilder.addQueryParameter("shouldDecrement", shouldDecrement);
							String favouritesUrl = favouritesUrlBuilder.build().toString();

						    RequestBody body = RequestBody.create(null, new byte[0]);
							
							Request favouritesReq = new Request.Builder()
									.url(favouritesUrl)
									.method("PUT", body)
									.build();

							Call favouritesCall = client.newCall(favouritesReq);
							Response responseFromFavourites = null;

							String favouritesBody = "{}";
							
							try {
								responseFromFavourites = favouritesCall.execute();
								favouritesBody = responseFromFavourites.body().string();
								if (!favouritesBody.contains("\"status\":\"OK\"") ) {
									dbQueryStatus = new DbQueryStatus("Update song favourites count was "
											+ "not successful", DbQueryExecResult.QUERY_ERROR_GENERIC);
								}
							} catch (IOException i) {
								i.printStackTrace();
							}
						} catch (Exception e) {
							e.printStackTrace();
							dbQueryStatus = new DbQueryStatus("Unable to contact song microservice to "
									+ "update song favourites", DbQueryExecResult.QUERY_ERROR_GENERIC);
						}
					}
				} else {
					dbQueryStatus = new DbQueryStatus("Error getting song with this id", 
							DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
			dbQueryStatus = new DbQueryStatus("Unable to contact song microservice to find song by id", 
					DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return dbQueryStatus;
	}
	
	/**
	 * Given a profile with specified userName, call the profileDriver function getAllSongFriendsLike to get a map
	 * from friends->songIds. Then in this function contact the song microservice to get songTitlesById, and store
	 * this map friends->songTitles in DbQueryStatus data field.
	 * 
	 * @param userName 			the userName of the specified Profile 
	 * @return DbQueryStatus 	the message, status, and result of running database query 
	 */
	public DbQueryStatus getFriendsSongIdsAndSongTitles(String userName) {
		DbQueryStatus dbQueryStatus = null;
		
		dbQueryStatus = profileDriver.getAllSongFriendsLike(userName);
		if(dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
			return new DbQueryStatus("User does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		} else if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_ERROR_GENERIC) {
			return new DbQueryStatus("Error getting friends song ids", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		
		Map<String, Object> userToSongId = (Map<String, Object>) dbQueryStatus.getData();
		
		JSONObject response = new JSONObject();
		
		try {
			for(String key : userToSongId.keySet()) {
				// Store the titles in a JSONArray. To be used in our data response
				JSONArray songTitles = new JSONArray();
				// Get the value associated with the user key and iterate over them. These values are song ids
				List<String> songIds = (List<String>) userToSongId.get(key);
				for (String id : songIds) {
					HttpUrl.Builder getSongUrlBuilder = HttpUrl.parse("http://localhost:3001" + "/getSongTitleById").newBuilder();
					getSongUrlBuilder.addPathSegment(id);
					String getSongUrl = getSongUrlBuilder.build().toString();

					Request getSongReq = new Request.Builder()
							.url(getSongUrl)
							.method("GET", null)
							.build();
					
					Call getSongCall = client.newCall(getSongReq);
					Response responseFromGetSong = null;
					
					responseFromGetSong = getSongCall.execute();
					
					// Convert the getSongTitleById to a json to get the title field
					JSONObject songData = new JSONObject(responseFromGetSong.body().string());
					
					songTitles.put(songData.get("data"));
				}
				response.put(key, songTitles);
				dbQueryStatus = new DbQueryStatus("Successfully retrieved friends songs", DbQueryExecResult.QUERY_OK);
				dbQueryStatus.setData(response.toMap());
			}
		} catch (Exception e) {
			e.printStackTrace();
			dbQueryStatus = new DbQueryStatus("Unable to contact song microservice to find song title by id", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		
		return dbQueryStatus;
	}
}