var arr = [3, 5, 7];
arr.foo = 'hello';

for (var i of arr) {
   console.log(i); // logs 3, 5, 7
}

// can be desugared as
{
    const _iterator = arr[Symbol.iterator]();
    let _result;
    let i;
    while(!(_result = _iterator.next()).done) {
        i = _result.value 
        console.log(i);
    }
}
