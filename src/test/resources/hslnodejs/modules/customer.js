 exports.getCustomer = function (req, res, db, collectionName) {
    res.writeHead(200, {'Content-Type': 'text/html'});
	var cursor = db.collection(String(collectionName)).find({ "id": { $eq: Number(req.params.id)}}).toArray(function(err, results) {
	  	for (var i = 0; i < results.length; i++) {
 	   		res.write(String(results[i]["id"]));
		}
		res.end();
	})
	/*
		TODO:
			// create and log critical account
			Account account = new Account(4242L, 1234, "savings", 1, 0);
	  		log.info("Account Data is {}", account);
	  		log.info("Customer Data is {}", customer);
  		    // send data to saleforce
  		    try {
 			     dispatchEventToSalesForce(String.format(" Customer %s Logged into SalesForce", customer));
    		} catch (Exception e) {
      			log.error("Failed to Dispatch Event to SalesForce . Details {} ", e.getLocalizedMessage());
    		}
	*/
};

 exports.loadSettings = function (req, res) {
 	console.log(req.cookies["settings"]);
    /*// get cookie values
    if (!checkCookie(request())) {
      return badRequest("cookie is incorrect");
    }
    String md5sum = request().getHeaders().get("Cookie").get().substring("settings=".length(), 41);
    File folder = new File("./static/");
    File[] listOfFiles = folder.listFiles();
    String filecontent = new String();
    for (File f : listOfFiles) {
      // not efficient, i know
      filecontent = new String();
      byte[] encoded = Files.readAllBytes(f.toPath());
      filecontent = new String(encoded, StandardCharsets.UTF_8);
      if (filecontent.contains(md5sum)) {
        // this will send me to the developer hell (if exists)

        // encode the file settings, md5sum is removed
        String s = new String(Base64.getEncoder().encode(filecontent.replace(md5sum, "").getBytes()));
        // setting the new cookie
        response().setHeader("Cookie", "settings=" + s + "," + md5sum);
        return ok();
      }
    }
    // bad req  could be 404
    // res.writeHeader(404)
    //	return badRequest();*/
  };