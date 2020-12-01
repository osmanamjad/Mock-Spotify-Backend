package com.csc301.songmicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();

	
	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongTitleById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId);
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}

	
	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public @ResponseBody Map<String, Object> deleteSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));

		// first delete the song from mongo db
		DbQueryStatus dbQueryStatus = songDal.deleteSongById(songId);
		
		if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) {
			try {
				HttpUrl.Builder deleteSongUrlBuilder = HttpUrl.parse("http://localhost:3002" + "/deleteAllSongsFromDb").newBuilder();
				deleteSongUrlBuilder.addPathSegment(songId);
				String deleteSongUrl = deleteSongUrlBuilder.build().toString();
				
			    RequestBody body = RequestBody.create(null, new byte[0]);

				Request deleteSongReq = new Request.Builder()
						.url(deleteSongUrl)
						.method("PUT", body)
						.build();

				Call deleteSongCall = client.newCall(deleteSongReq);
				Response responseFromDeleteSong = null;

				String deleteSongBody = "{}";

				try {
					responseFromDeleteSong = deleteSongCall.execute();
					deleteSongBody = responseFromDeleteSong.body().string();
					
					//if song doesnt exist in neo4j db, or its been successfully deleted, then status is OK
					if (!deleteSongBody.contains("\"status\":\"OK\"")) {
						dbQueryStatus = new DbQueryStatus("Error deleting song from neo4j db", 
								DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
				dbQueryStatus = new DbQueryStatus("Unable to contact profile microservice to delete song "
						+ "from neo4j db", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}

	
	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addSong(@RequestParam Map<String, String> params,
			HttpServletRequest request) {
		
		DbQueryStatus dbQueryStatus = null;
		
		if (params.get("songName") == null || params.get("songArtistFullName") == null || 
				params.get("songAlbum") == null) {
			dbQueryStatus = new DbQueryStatus("Missing query params", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		else {
			Song song = new Song(params.get("songName"), params.get("songArtistFullName"), 
					params.get("songAlbum"));
			dbQueryStatus = songDal.addSong(song);

		}
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
	
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/updateSongFavouritesCount/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> updateFavouritesCount(@PathVariable("songId") String songId,
			@RequestParam("shouldDecrement") String shouldDecrement, HttpServletRequest request) {

		DbQueryStatus dbQueryStatus = null;
		
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("data", String.format("PUT %s", Utils.getUrl(request)));
		
		boolean shouldDec;
		if (shouldDecrement.equalsIgnoreCase("true")) {
			shouldDec = true;
			dbQueryStatus = songDal.updateSongFavouritesCount(songId, shouldDec);
		} else if (shouldDecrement.equalsIgnoreCase("false")) {
			shouldDec = false;
			dbQueryStatus = songDal.updateSongFavouritesCount(songId, shouldDec);
		} else {
			dbQueryStatus = new DbQueryStatus("Invalid shouldDecrement value", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}
}