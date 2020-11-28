package com.csc301.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		
		return null;
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		
		return null;
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
			
		return null;
	}
}
