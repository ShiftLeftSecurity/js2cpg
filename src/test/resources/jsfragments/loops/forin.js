var car = new Object();
car.make = "Ford";
car.model = "Mustang";

var arr = [3, 5, 7];
arr.foo = 'hello';

for (var i in car) {
  console.log(car[i]);
}

for (var i in arr) {
   console.log(i); 
}

// first step -- use Object.keys and make it an of loop
for (var i of Object.keys(car)) {
  console.log(car[i]);
}

for (var i of Object.keys(arr)) {
   console.log(i); 
}

// second step -- used for of desugaring
{
    const _iterator = Object.keys(car)[Symbol.iterator]();
    let _result;
    let i;
    while(!(_result = _iterator.next()).done) {
        i = _result.value;
        console.log(i);
    }
}

{
    const _iterator = Object.keys(arr)[Symbol.iterator]();
    let _result;
    let i;
    while(!(_result = _iterator.next()).done) {
        i = _result.value;
        console.log(i);
    }
}


