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

	@RequestMapping(value = "/profile", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addProfile(@RequestParam Map<String, String> params,
			HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;
		
		if (params.get("userName") == null || params.get("fullName") == null || 
				params.get("password") == null) {
			dbQueryStatus = new DbQueryStatus("Missing query params", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		else {
			dbQueryStatus = profileDriver.createUserProfile(params.get("userName"), params.get("fullName"), 
					params.get("password"));
		}
		
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
	
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

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

	@RequestMapping(value = "/getAllFriendFavouriteSongTitles/{userName}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getAllFriendFavouriteSongTitles(@PathVariable("userName") String userName,
			HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));
				
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}


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
	
	public DbQueryStatus likeSongAndUpdateFavourites(String userName, String songId, String shouldDecrement) {
		DbQueryStatus dbQueryStatus = null;

		try {
			HttpUrl.Builder getSongUrlBuilder = HttpUrl.parse("http://localhost:3001" + "/getSongById").newBuilder();
			getSongUrlBuilder.addPathSegment(songId);
			String getSongUrl = getSongUrlBuilder.build().toString();
			
			System.out.println(getSongUrl);

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
				System.out.println(getSongBody);
				
				// if /getSongById found a song successfully, then we attempt to add/remove it to playlist
				if (getSongBody.contains("\"status\":\"OK\"")) {
					if (shouldDecrement == "false") {
						dbQueryStatus = playlistDriver.likeSong(userName, songId);
					} else {
						dbQueryStatus = playlistDriver.unlikeSong(userName, songId);
					}
					
					//if it was successfully liked and added to playlist then update its favourites count
					if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) {
						try {
							HttpUrl.Builder favouritesUrlBuilder = HttpUrl.parse("http://localhost:3001" + "/updateSongFavouritesCount").newBuilder();
							favouritesUrlBuilder.addPathSegment(songId);
							favouritesUrlBuilder.addQueryParameter("shouldDecrement", shouldDecrement);
							String favouritesUrl = favouritesUrlBuilder.build().toString();
							
							System.out.println(favouritesUrl);

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
								System.out.println(favouritesBody);
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
					dbQueryStatus = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
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
}