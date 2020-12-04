package com.csc301.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			}
			session.close();
		}
	}
	
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		
		DbQueryStatus dbqs = null;
		
		String queryProfile;
		String queryPlaylist;
		String queryRelation;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				try {
					queryProfile = "MERGE (p:profile {userName: $x, fullName: $y, password: $z})";
					queryPlaylist = "MERGE (pl:playlist {plName: $x})";
					queryRelation = "MATCH (nProfile:profile {userName: $x}),(nPlaylist:playlist {plName:$y})\n"
							+ "MERGE (nProfile)-[:created]->(nPlaylist)";
					
					Map<String, Object> paramsProfile = new HashMap<String, Object>();
					paramsProfile.put("x", userName);
					paramsProfile.put("y", fullName);
					paramsProfile.put("z", password);
					
					Map<String, Object> paramsPlaylist = new HashMap<String, Object>();
					paramsPlaylist.put("x", userName + "-favorites");
					
					Map<String, Object> paramsRelation = new HashMap<String, Object>();
					paramsRelation.put("x", userName);
					paramsRelation.put("y", userName + "-favorites");
					
					trans.run(queryProfile, paramsProfile);
					trans.run(queryPlaylist, paramsPlaylist);
					trans.run(queryRelation, paramsRelation);
					
					trans.success();					
				} catch (Exception e) {
					return new DbQueryStatus("Error adding profile and playlist", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			} 
			session.close();
		}
		dbqs = new DbQueryStatus("Successfully added profile and playlist", DbQueryExecResult.QUERY_OK);
		return dbqs;
	}

	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		
		DbQueryStatus dbqs = null;
		
		try (Session session = ProfileMicroserviceApplication.driver.session()){
			try (Transaction trans = session.beginTransaction()) {
				try {	
					if(userName.equals(frndUserName)) {
						return new DbQueryStatus("Can not follow yourself", DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
					
					if (!isNameInDB(userName, "profile") || !isNameInDB(frndUserName, "profile")) {
						return new DbQueryStatus("User do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					if(isRelationshipInDB(userName, frndUserName)) {
						return new DbQueryStatus(userName + " already follows " + frndUserName, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					String query = "MATCH (a:profile {userName: $x}), (b:profile {userName: $y})\n"
						+ "MERGE (a)-[:follows]->(b)";
					
					Map<String, Object> queryParams = new HashMap<String, Object>();
					queryParams.put("x", userName);
					queryParams.put("y", frndUserName);
					
					trans.run(query, queryParams);
					
					trans.success();
				} catch (Exception e)  {
					return new DbQueryStatus("Error following user", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		
		dbqs = new DbQueryStatus(userName + " follows " + frndUserName, DbQueryExecResult.QUERY_OK);
		return dbqs;
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		
		DbQueryStatus dbqs = null;
		
		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				try {
					if(userName.equals(frndUserName)) {
						return new DbQueryStatus("Can not unfollow yourself. (Can't follow yourself to begin with)", DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
					
					if (!isNameInDB(userName, "profile") || !isNameInDB(frndUserName, "profile")) {
						return new DbQueryStatus("User do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					if(!isRelationshipInDB(userName, frndUserName)) {
						return new DbQueryStatus(userName + " does not follow " + frndUserName +". Can't unfollow a user you don't follow", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					String query = "MATCH ({userName: $x})-[r:follows]->({userName: $y})\nDELETE r";
					
					Map<String, Object> queryParams = new HashMap<String, Object>();
					queryParams.put("x", userName);
					queryParams.put("y", frndUserName);
					
					trans.run(query, queryParams);
					
					trans.success();
					
				} catch (Exception e) {
					return new DbQueryStatus("Error removing follow", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		
		dbqs = new DbQueryStatus(userName + " unfollows " + frndUserName, DbQueryExecResult.QUERY_OK);
		return dbqs;
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		
		DbQueryStatus dbqs = null;
		
		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				try {
					String query;
					Map<String, Object> queryParams;
					Record record = null;
					StatementResult result = null;
					if(!isNameInDB(userName, "profile")) {
						return new DbQueryStatus("User does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					}
					
					// Get the friends of parameter userName
					query = "MATCH ({userName: $x})-[r:follows]->(n)\n"
							+ "RETURN n.userName";
					queryParams = new HashMap<String, Object>();
					queryParams.put("x", userName);
					
					result = trans.run(query, queryParams);
					queryParams.clear();
					
					List<String> friends = new ArrayList<String>();
					while(result.hasNext()) {
						record = result.next();
						friends.add(record.get("n.userName").asString());
					}
					
					Map<String, Object> userToSongId = new HashMap<String, Object>();
					// For each friend, get the songIds in there playlists and store them in a hashmap
					for(String user: friends) {
						query = "MATCH (:profile {userName: $x})-[:created]->(:playlist)-[:includes]->(s:song)\n"
								+ "RETURN s.songId";
						queryParams.put("x", user);
						result = trans.run(query, queryParams);
						
						List<String> songIds = new ArrayList<String>();
						if(!result.hasNext()) {
							userToSongId.put(user, songIds);
						}
						while(result.hasNext()) {
							record = result.next();
							songIds.add(record.get("s.songId").asString());
						}
						userToSongId.put(user, songIds);
					}
					
					dbqs = new DbQueryStatus("Retrieving " + userName+ " friends song ids", DbQueryExecResult.QUERY_OK);
					dbqs.setData(userToSongId);
					trans.success();
					
				} catch (Exception e) {
					return new DbQueryStatus("Error getting friends song ids", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
			session.close();
		}
		return dbqs;
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
	
	public boolean isRelationshipInDB(String userName, String frndUserName) throws Exception {
		Session session = driver.session();
		Transaction trans = session.beginTransaction();
		String query = "MATCH ({userName: $x})-[r]->({userName: $y})\n"
				+ "RETURN type(r)";
		Map<String, Object> queryParams = new HashMap<String, Object>();
		queryParams.put("x", userName);
		queryParams.put("y", frndUserName);
		StatementResult result = trans.run(query, queryParams);
		while(result.hasNext()) {
			Record record = result.next();
			if(record.get(0).asString().equals("follows")){
				return true;
			}
		}
		return false;
	}
}
