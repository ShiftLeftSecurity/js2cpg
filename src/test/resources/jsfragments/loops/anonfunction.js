function global () {
    console.log("boo");
}

global();


var functionObject = function(a,b) {
    console.log("a: " + a + ",b:" + b);
};

functionObject(1,2);


// a constructor function
function User(name) {
    this.name = name;
}

User.prototype.showName = function() {
    console.log(this.name);
};

let user = new User("John");
user.showName();


class User2 {
    constructor(name) { this.name = name; }
    showName() { console.log(this.name); }
}

let user2 = new User2("John");

user2.showName();

