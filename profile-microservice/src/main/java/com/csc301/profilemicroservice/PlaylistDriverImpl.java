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

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		DbQueryStatus dbqs = null;
		
		try (Session session = ProfileMicroserviceApplication.driver.session()){
			try (Transaction trans = session.beginTransaction()) {
				try {	
					if (!isNameInDB(userName, "profile")) {
						return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
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

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		
		return null;
	}

	@Override
	public DbQueryStatus deleteSongFromDb(String songId) {
		
		return null;
	}
	
	public boolean isNameInDB(String name, String type) throws Exception {
		Session session = driver.session();
		Transaction trans = session.beginTransaction();
		StatementResult result = trans.run("MATCH (n:" + type + ") RETURN n.userName");
		while(result.hasNext()) {
			Record record = result.next();
			if (name.equals(record.get("n.userName").asString())) {
				return true;
			}
		}
		return false;
	}
}
