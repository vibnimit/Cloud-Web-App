$( document ).ready(function() {
    console.log( "ready!" );

    $("#sendReq").click(function() {
  		console.log( "Handler for .click() called." );
  		$.ajax({
  			url: "recognizeObject/",
  			type: "GET",
        retryCount: 0,
        retryLimit: 1,
			success: function(data){
				console.log(data)
			}

		});
	});
});