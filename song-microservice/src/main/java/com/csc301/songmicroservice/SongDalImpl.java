package com.csc301.songmicroservice;

import java.util.HashMap;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DbQueryStatus findSongById(String songId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DbQueryStatus deleteSongById(String songId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId, boolean shouldDecrement) {
		int change;
		if (shouldDecrement) {
			change = -1;
		} else {
			change = 1;
		}
		DbQueryStatus dbqs = null;
		try {
	        BasicDBObject query = new BasicDBObject();
	        BasicDBObject update = new BasicDBObject();
	        query.put("_id", new ObjectId(songId));
	        
	        Map<String, Integer> fieldToUpdate = new HashMap<String, Integer>();
	        fieldToUpdate.put("songAmountFavourites", change); // set the value to increment as change
	        update.put("$inc", fieldToUpdate); //set the operation as an increment       
	        
	        if(db.getCollection("songs").findOneAndUpdate(query, update) != null) {
	        	dbqs = new DbQueryStatus("Changed song amount favourites by id", DbQueryExecResult.QUERY_OK);
	        } else {
	        	return new DbQueryStatus("Error updating song amount favourites", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        }
		} catch (Exception e) {
			return new DbQueryStatus("Error song with id does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		}
		
		return dbqs;
	}
}