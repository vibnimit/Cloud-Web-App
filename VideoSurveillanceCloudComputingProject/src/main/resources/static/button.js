$( document ).ready(function() {
    console.log( "ready!" );

    $("#sendReq").click(function() {
      console.log("request sent!");
      $this = $(this);
      $.ajax({
  			url: "recognizeObject/",
  			type: "GET",
        retryCount: 0,
        retryLimit: 1,
			success: function(data){
				console.log(data)
        $("#result").append("<p>"+data+"</p>");
			}

		});
	});
});