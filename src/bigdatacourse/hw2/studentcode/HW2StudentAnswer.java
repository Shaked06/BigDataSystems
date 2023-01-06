package bigdatacourse.hw2.studentcode;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Set;


import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import bigdatacourse.hw2.HW2API;

public class HW2StudentAnswer implements HW2API{
	
	// general consts
	public static final String		NOT_AVAILABLE_VALUE 	=			"na";
	public static final	double		NOT_AVAILABLE_DOUBLE_VALUE 	=		-1.0;
	
	// CQL stuff
	//TODO: add here create table and query designs 
	private static final String		TABLE_ITEMS	 = "items";
	private static final String		TABLE_REVIEW_BY_ITEM = "reviewByItem";
	private static final String		TABLE_REVIEW_BY_USER = "reviewByUser";
	
	// ITEMS TABLE QUERIES
	private static final String		CQL_CREATE_TABLE_ITEMS = 
			"CREATE TABLE " + TABLE_ITEMS	+" (" 	+ 
				"asin TEXT,"			+
				"title TEXT,"			+
				"image TEXT,"			+
				"categories SET<text>,"	+
				"description TEXT,"		+
				"PRIMARY KEY (asin)"	+
			") ";
	
	private static final String		CQL_CREATE_TABLE_REVIEW_BY_ITEM = 
			"CREATE TABLE " + TABLE_REVIEW_BY_ITEM	+" (" 	+ 
					"asin TEXT,"			+
					"reviewer_id TEXT,"		+
					"time TIMESTAMP,"		+
					"reviewer_name TEXT,"	+
					"rating DOUBLE, "			+
					"summary TEXT, "		+
					"review_text TEXT, "	+
					"PRIMARY KEY ((asin), time, reviewer_id)"	+
				") " +
				"WITH CLUSTERING ORDER BY (time DESC, reviewer_id ASC)";
	private static final String		CQL_CREATE_TABLE_REVIEW_BY_USER = 
			"CREATE TABLE " + TABLE_REVIEW_BY_USER	+" (" 	+ 
					"asin TEXT,"			+
					"reviewer_id TEXT,"		+
					"time TIMESTAMP,"		+
					"reviewer_name TEXT,"	+
					"rating DOUBLE, "			+
					"summary TEXT, "		+
					"review_text TEXT, "	+
					"PRIMARY KEY ((reviewer_id), time, asin)"	+
				") " +
				"WITH CLUSTERING ORDER BY (time DESC, asin ASC)";

	private static final String		CQL_ITEMS_INSERT = 
			"INSERT INTO " + TABLE_ITEMS + "(asin, title, image, categories, description) VALUES(?, ?, ?, ?, ?)";
	private static final String		CQL_ITEMS_SELECT = 
			"SELECT * FROM " + TABLE_ITEMS + " WHERE asin = ?";

	private static final String		CQL_REVIEW_BY_ITEM_INSERT = 
			"INSERT INTO " + TABLE_REVIEW_BY_ITEM + "(asin, reviewer_id, time, reviewer_name, rating, summary, review_text) VALUES(?, ?, ?, ?, ?, ?, ?)";
	private static final String		CQL_REVIEW_BY_ITEM_SELECT = 
			"SELECT * FROM " + TABLE_REVIEW_BY_ITEM + " WHERE asin = ?";
	
	private static final String		CQL_REVIEW_BY_USER_INSERT = 
			"INSERT INTO " + TABLE_REVIEW_BY_USER + "(reviewer_id, time, asin, reviewer_name, rating, summary, review_text) VALUES(?, ?, ?, ?, ?, ?, ?)";
	private static final String		CQL_REVIEW_BY_USER_SELECT = 
			"SELECT * FROM " + TABLE_REVIEW_BY_USER + " WHERE reviewer_id = ?";
	
	// cassandra session
	private CqlSession session;
	
	// prepared statements
	PreparedStatement pstmtItemsAdd;
	PreparedStatement pstmtItemsSelect;
	PreparedStatement pstmtreviewByItemAdd;
	PreparedStatement pstmtreviewByItemSelect;
	PreparedStatement pstmtreviewByUserAdd;
	PreparedStatement pstmtreviewByUserSelect;
	
	@Override
	public void connect(String pathAstraDBBundleFile, String username, String password, String keyspace) {
		if (session != null) {
			System.out.println("ERROR - cassandra is already connected");
			return;
		}
		
		System.out.println("Initializing connection to Cassandra...");
		
		this.session = CqlSession.builder()
				.withCloudSecureConnectBundle(Paths.get(pathAstraDBBundleFile))
				.withAuthCredentials(username, password)
				.withKeyspace(keyspace)
				.build();
		
		System.out.println("Initializing connection to Cassandra... Done");
	}


	@Override
	public void close() {
		if (session == null) {
			System.out.println("Cassandra connection is already closed");
			return;
		}
		
		System.out.println("Closing Cassandra connection...");
		session.close();
		System.out.println("Closing Cassandra connection... Done");
	}

	
	
	@Override
	public void createTables() {
//		session.execute(CQL_CREATE_TABLE_ITEMS);
//		System.out.println("created table: " + TABLE_ITEMS);
		session.execute(CQL_CREATE_TABLE_REVIEW_BY_ITEM);
		System.out.println("created table: " + TABLE_REVIEW_BY_ITEM);
		session.execute(CQL_CREATE_TABLE_REVIEW_BY_USER);
		System.out.println("created table: " + TABLE_REVIEW_BY_USER);
	}

	@Override
	public void initialize() {
		pstmtItemsAdd = session.prepare(CQL_ITEMS_INSERT);
		pstmtItemsSelect = session.prepare(CQL_ITEMS_SELECT);
		
		pstmtreviewByItemAdd = session.prepare(CQL_REVIEW_BY_ITEM_INSERT);
		pstmtreviewByItemSelect = session.prepare(CQL_REVIEW_BY_ITEM_SELECT);
		
		pstmtreviewByUserAdd = session.prepare(CQL_REVIEW_BY_USER_INSERT);
		pstmtreviewByUserSelect = session.prepare(CQL_REVIEW_BY_USER_SELECT);
	}

	@Override
	public void loadItems(String pathItemsFile) throws Exception {

		String line;
		int maxThreads	= 100;
		String file = "data/meta_Office_Products.json";

		//	creating the thread factors
		ExecutorService executor = Executors.newFixedThreadPool(maxThreads);		
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            while ((line = br.readLine()) != null) {
            	JSONObject json = new JSONObject(line);
            	executor.execute(new Runnable() {
    				@Override
    				public void run() {
    					insertItem(session, pstmtItemsAdd, json);
    				}
    			});
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } finally {
            br.close();
        }
		
	}

	@Override
	public void loadReviews(String pathReviewsFile) throws Exception {
		String line;
		int maxThreads	= 100;
		String file = "data/reviews_Office_Products.json";

		//	creating the thread factors
		ExecutorService executor = Executors.newFixedThreadPool(maxThreads);		
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            while ((line = br.readLine()) != null) {
            	JSONObject json = new JSONObject(line);
            	executor.execute(new Runnable() {
    				@Override
    				public void run() {
    					insertReview(session, pstmtreviewByItemAdd, pstmtreviewByUserAdd, json);
    				}
    			});
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } finally {
            br.close();
        }
		
	}

	@Override
	public void item(String asin) {
		BoundStatement bstmt = pstmtItemsSelect.bind().setString("asin", asin);
		ResultSet rs = session.execute(bstmt);
		Row row = rs.one();
		if (row != null) {
			System.out.println("asin: " 		+ row.getString("asin"));
			System.out.println("title: " 		+ row.getString("title"));
			System.out.println("image: " 		+ row.getString("image"));
			System.out.println("categories: " 	+ new TreeSet<String>(row.getSet("categories", String.class)));
			System.out.println("description: " 	+ row.getString("description"));
			row = rs.one();
		}
		else {
			// required format - if the asin does not exists return this value
			System.out.println("not exists");
		}

		// required format - example for asin B005QB09TU
//		System.out.println("asin: " 		+ "B005QB09TU");
//		System.out.println("title: " 		+ "Circa Action Method Notebook");
//		System.out.println("image: " 		+ "http://ecx.images-amazon.com/images/I/41ZxT4Opx3L._SY300_.jpg");
//		System.out.println("categories: " 	+ new TreeSet<String>(Arrays.asList("Notebooks & Writing Pads", "Office & School Supplies", "Office Products", "Paper")));
//		System.out.println("description: " 	+ "Circa + Behance = Productivity. The minute-to-minute flexibility of Circa note-taking meets the organizational power of the Action Method by Behance. The result is enhanced productivity, so you'll formulate strategies and achieve objectives even more efficiently with this Circa notebook and project planner. Read Steve's blog on the Behance/Levenger partnership Customize with your logo. Corporate pricing available. Please call 800-357-9991.");;
	}
	
	
	@Override
	public void userReviews(String reviewerID) {
		// the order of the reviews should be by the time (desc), then by the asin
		BoundStatement bstmt = pstmtreviewByUserSelect.bind().setString("reviewer_id", reviewerID);
		ResultSet rs = session.execute(bstmt);
		Row row = rs.one();
		int count = 0;
		while (row != null) {
			System.out.println(	
				"time: " 			+ row.getInstant("time") + 
				", asin: " 			+ row.getString("asin") 	+
				", reviewerID: " 	+ row.getString("reviewer_id") 	+
				", reviewerName: " 	+ row.getString("reviewer_name")	+
				", rating: " 		+ row.getDouble("rating") 	+ 
				", summary: " 		+ row.getString("summary")	+
				", reviewText: " 	+ row.getString("review_text")
			);
			count+=1;
			row = rs.one();

		}
		System.out.println("total reviews: " + count);
		
		// required format - example for reviewerID A17OJCRPMYWXWV
//		System.out.println(	
//				"time: " 			+ Instant.ofEpochSecond(1362614400) + 
//				", asin: " 			+ "B005QDG2AI" 	+
//				", reviewerID: " 	+ "A17OJCRPMYWXWV" 	+
//				", reviewerName: " 	+ "Old Flour Child"	+
//				", rating: " 		+ 5 	+ 
//				", summary: " 		+ "excellent quality"	+
//				", reviewText: " 	+ "These cartridges are excellent .  I purchased them for the office where I work and they perform  like a dream.  They are a fraction of the price of the brand name cartridges.  I will order them again!");
//
//		System.out.println(	
//				"time: " 			+ Instant.ofEpochSecond(1360108800) + 
//				", asin: " 			+ "B003I89O6W" 	+
//				", reviewerID: " 	+ "A17OJCRPMYWXWV" 	+
//				", reviewerName: " 	+ "Old Flour Child"	+
//				", rating: " 		+ 5 	+ 
//				", summary: " 		+ "Checkbook Cover"	+
//				", reviewText: " 	+ "Purchased this for the owner of a small automotive repair business I work for.  The old one was being held together with duct tape.  When I saw this one on Amazon (where I look for almost everything first) and looked at the price, I knew this was the one.  Really nice and very sturdy.");


	}

	@Override
	public void itemReviews(String asin) {
		// the order of the reviews should be by the time (desc), then by the reviewerID
		BoundStatement bstmt = pstmtreviewByItemSelect.bind().setString("asin", asin);
		ResultSet rs = session.execute(bstmt);
		Row row = rs.one();
		int count = 0;
		while (row != null) {
			System.out.println(	
				"time: " 			+ row.getInstant("time") + 
				", asin: " 			+ row.getString("asin") 	+
				", reviewerID: " 	+ row.getString("reviewer_id") 	+
				", reviewerName: " 	+ row.getString("reviewer_name")	+
				", rating: " 		+ row.getDouble("rating") 	+ 
				", summary: " 		+ row.getString("summary")	+
				", reviewText: " 	+ row.getString("review_text")
			);
			count+=1;
			row = rs.one();

		}
		System.out.println("total reviews: " + count);
		
		// required format - example for asin B005QDQXGQ
//		System.out.println(	
//				"time: " 			+ Instant.ofEpochSecond(1391299200) + 
//				", asin: " 			+ "B005QDQXGQ" 	+
//				", reviewerID: " 	+ "A1I5J5RUJ5JB4B" 	+
//				", reviewerName: " 	+ "T. Taylor \"jediwife3\""	+
//				", rating: " 		+ 5 	+ 
//				", summary: " 		+ "Play and Learn"	+
//				", reviewText: " 	+ "The kids had a great time doing hot potato and then having to answer a question if they got stuck with the &#34;potato&#34;. The younger kids all just sat around turnin it to read it.");
//
//		System.out.println(	
//				"time: " 			+ Instant.ofEpochSecond(1390694400) + 
//				", asin: " 			+ "B005QDQXGQ" 	+
//				", reviewerID: " 	+ "AF2CSZ8IP8IPU" 	+
//				", reviewerName: " 	+ "Corey Valentine \"sue\""	+
//				", rating: " 		+ 1 	+ 
//				", summary: " 		+ "Not good"	+
//				", reviewText: " 	+ "This Was not worth 8 dollars would not recommend to others to buy for kids at that price do not buy");
//
//		System.out.println(	
//				"time: "			+ Instant.ofEpochSecond(1388275200) + 
//				", asin: " 			+ "B005QDQXGQ" 	+
//				", reviewerID: " 	+ "A27W10NHSXI625" 	+
//				", reviewerName: " 	+ "Beth"	+
//				", rating: " 		+ 2 	+ 
//				", summary: " 		+ "Way overpriced for a beach ball"	+
//				", reviewText: " 	+ "It was my own fault, I guess, for not thoroughly reading the description, but this is just a blow-up beach ball.  For that, I think it was very overpriced.  I thought at least I was getting one of those pre-inflated kickball-type balls that you find in the giant bins in the chain stores.  This did have a page of instructions for a few different games kids can play.  Still, I think kids know what to do when handed a ball, and there's a lot less you can do with a beach ball than a regular kickball, anyway.");

		
	}
	
	
	


	// --------------- NEW FUNCTIONS -----------------------
	public static void insertItem(CqlSession session, PreparedStatement pstmt, JSONObject json) {
	    
	    String asin = json.getString("asin");
	    String title = (json.has("title") ? json.getString("title") : NOT_AVAILABLE_VALUE);
	    String image = (json.has("imUrl") ? json.getString("imUrl") : NOT_AVAILABLE_VALUE);
	    String description = (json.has("description") ? json.getString("description") : NOT_AVAILABLE_VALUE);
	    JSONArray arrCategories = json.getJSONArray("categories");
	    Set<String> categories = new HashSet<String>();
	    
	    for (int i=0; i<arrCategories.length(); i++) {
	    	JSONArray arr = arrCategories.getJSONArray(i);
	    	for (Object o: arr) {
	    		if (o instanceof String) {
	    			categories.add(o.toString());
	    		}
	    	}
	    }

		BoundStatement bstmt = pstmt.bind()
		.setString("asin", asin)
		.setSet("categories", categories, String.class)
		.setString("description", description)
		.setString("image", image)
		.setString("title", title);

		session.execute(bstmt);
	}
	
	public static void insertReview(CqlSession session, PreparedStatement pstmtByItem, PreparedStatement pstmtByUser, JSONObject json) {
		
		long ts = json.getLong("unixReviewTime");
	    String asin = json.getString("asin");
	    String reviewerID = json.getString("reviewerID");
	    String reviewerName = (json.has("reviewerName") ? json.getString("reviewerName") : NOT_AVAILABLE_VALUE);
	    double rating = (json.has("overall") ? json.getDouble("overall") : NOT_AVAILABLE_DOUBLE_VALUE);
	    String summary = (json.has("summary") ? json.getString("summary") : NOT_AVAILABLE_VALUE);
	    String reviewText = (json.has("reviewText") ? json.getString("reviewText") : NOT_AVAILABLE_VALUE);
	    

		BoundStatement bstmtByItem = pstmtByItem.bind()
		.setInstant("time", Instant.ofEpochSecond(ts))
		.setString("asin", asin)
		.setString("reviewer_id", reviewerID)
		.setString("reviewer_name", reviewerName)
		.setDouble("rating", rating)
		.setString("summary", summary)
		.setString("review_text", reviewText);

		session.execute(bstmtByItem);
		
		BoundStatement bstmtByUser = pstmtByUser.bind()
		.setInstant("time", Instant.ofEpochSecond(ts))
		.setString("asin", asin)
		.setString("reviewer_id", reviewerID)
		.setString("reviewer_name", reviewerName)
		.setDouble("rating", rating)
		.setString("summary", summary)
		.setString("review_text", reviewText);

		session.execute(bstmtByUser);		
	}

}
		