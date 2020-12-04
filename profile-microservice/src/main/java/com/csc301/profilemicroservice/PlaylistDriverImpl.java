package com.csc301.profilemicroservice;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			}
			session.close();
		}
	}

	/**
	* Returns a DbQueryStatus object used to extract status and data information
	* The DbQueryStatus object includes a dbQueryExecResult value.
	* The function creates an "includes" relationship between the username's playlist and songId 
	*
	* @param  userName  	the userName part of the playlist to add the song to
	* @param  songId 		the songId of the song node
	* @return DbQueryStatus the message, status, and result of running database query to like song
	*/
	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		DbQueryStatus dbqs = null;
		
		try (Session session = ProfileMicroserviceApplication.driver.session()){
			try (Transaction trans = session.beginTransaction()) {
				try {	
					if (!isNameInDB(userName)) {
						return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					if (isRelationshipInDB(userName, songId)) {
						return new DbQueryStatus("Song has been liked already", DbQueryExecResult.QUERY_OK);
					}
					String querySong = "MERGE (s:song {songId: $x})";
					Map<String, Object> querySongParam = new HashMap<String, Object>();
					querySongParam.put("x", songId);
					
					trans.run(querySong, querySongParam);
					
					String queryLike = "MATCH (p:playlist {plName: $x}), (s:song {songId: $y})\n"
						+ "MERGE (p)-[:includes]->(s)";
					
					Map<String, Object> queryLikeParams = new HashMap<String, Object>();
					queryLikeParams.put("x", userName + "-favorites");
					queryLikeParams.put("y", songId);
					
					trans.run(queryLike, queryLikeParams);
					
					trans.success();
				} catch (Exception e)  {
					return new DbQueryStatus("Error liking song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		
		dbqs = new DbQueryStatus("Successfully liked song", DbQueryExecResult.QUERY_OK);
		return dbqs;
	}

	/**
	* Returns a DbQueryStatus object used to extract status and data information
	* The DbQueryStatus object includes a dbQueryExecResult value.
	* The function removes the "includes" relationship between the username's playlist and songId 
	*
	* @param  userName  	the userName part of the playlist to remove the song from
	* @param  songId 		the songId of the song node
	* @return DbQueryStatus the message, status, and result of running database query to unlike song
	*/
	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		DbQueryStatus dbqs = null;

		try (Session session = ProfileMicroserviceApplication.driver.session()){
			try (Transaction trans = session.beginTransaction()) {
				try {	
					if (!isNameInDB(userName)) {
						return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					if (!isRelationshipInDB(userName, songId)) {
						return new DbQueryStatus("Relationship not found between user and song", 
								DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					String queryRemoveRel = "MATCH (p:playlist { plName: $x })-[r:includes]->"
							+ "(s: song {songId: $y})\nDELETE r";
					
					Map<String, Object> queryRemoveRelParam = new HashMap<String, Object>();
					queryRemoveRelParam.put("x", userName + "-favorites");
					queryRemoveRelParam.put("y", songId);
					
					trans.run(queryRemoveRel, queryRemoveRelParam);
					
					trans.success();
				} catch (Exception e)  {
					return new DbQueryStatus("Error unliking song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		
		dbqs = new DbQueryStatus("Successfully unliked song", DbQueryExecResult.QUERY_OK);
		return dbqs;
	}

	/**
	* Returns a DbQueryStatus object used to extract status and data information
	* The DbQueryStatus object includes a dbQueryExecResult value.
	* The function removes the song with the specified songId from the neo4j database 
	*
	* @param  songId 		the songId of the song node to remove
	* @return DbQueryStatus the message, status, and result of running database query to delete song
	*/
	@Override
	public DbQueryStatus deleteSongFromDb(String songId) {
		DbQueryStatus dbqs = null;

		try (Session session = ProfileMicroserviceApplication.driver.session()){
			try (Transaction trans = session.beginTransaction()) {
				try {	
					if (!isSongInDB(songId)) {
						// since this api will be called from song microservice when we delete a song from
						// mongo db, if the song doesn't exist in neo4j db it is not an error.
						return new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_OK);
					}
					
					//query to remove song and all its relationships
					String queryRemoveSong = "MATCH (s:song { songId: $x })\nDETACH DELETE s";
					
					Map<String, Object> queryRemoveSongParam = new HashMap<String, Object>();
					queryRemoveSongParam.put("x", songId);
					
					trans.run(queryRemoveSong, queryRemoveSongParam);
					
					trans.success();
				} catch (Exception e)  {
					return new DbQueryStatus("Error deleting song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		
		dbqs = new DbQueryStatus("Successfully deleted song", DbQueryExecResult.QUERY_OK);
		return dbqs;
	}
	
	/**
	* Returns a boolean representing whether or not the a profile with a certain username exists
	* in neo4j database
	*
	* @param  userName  		a userName to check if it exists in the neo4j database
	* @return boolean 			a boolean representing whether or not profile with that username exists
	*/
	public boolean isNameInDB(String userName) throws Exception {
		Session session = driver.session();
		Transaction trans = session.beginTransaction();
		StatementResult result = trans.run("MATCH (n:profile) RETURN n.userName");
		while(result.hasNext()) {
			Record record = result.next();
			if (userName.equals(record.get("n.userName").asString())) {
				return true;
			}
		}
		return false;
	}
	
	/**
	* Returns a boolean representing whether the song with the songId exists in the neo4j database
	*
	* @param  songId  			a songId to check if it exists in the neo4j database
	* @return boolean 			a boolean representing whether a song with that songId exists
	*/
	public boolean isSongInDB(String songId) throws Exception {
		Session session = driver.session();
		Transaction trans = session.beginTransaction();
		StatementResult result = trans.run("MATCH (n:song) RETURN n.songId");
		while(result.hasNext()) {
			Record record = result.next();
			if (songId.equals(record.get("n.songId").asString())) {
				return true;
			}
		}
		return false;
	}
	
	/**
	* Returns a boolean representing whether or not an "includes" relationship exists between 
	* a playlist and song
	*
	* @param  userName  		the username part of the playlist
	* @param  songId			the songId of the song node  
	* @return boolean 			a boolean representing whether relationship exists between song and playlist
	*/
	public boolean isRelationshipInDB(String userName, String songId) throws Exception {
		Session session = driver.session();
		Transaction trans = session.beginTransaction();
		String query = "MATCH ({plName: $x})-[r]->({songId: $y})\n"
				+ "RETURN type(r)";
		Map<String, Object> queryParams = new HashMap<String, Object>();
		queryParams.put("x", userName + "-favorites");
		queryParams.put("y", songId);
		StatementResult result = trans.run(query, queryParams);
		while(result.hasNext()) {
			Record record = result.next();
			if(record.get(0).asString().equals("includes")){
				return true;
			}
		}
		return false;
	}
}
