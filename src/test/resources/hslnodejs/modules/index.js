exports.getIndex = function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/html'});
	res.write("Hello Shiftleft");
	res.end();
};