require(["jquery","widgets/likedlist", "widgets/likedaddbox", "widgets/login"], function($, LikedList, LikedAddBox, Login) {
   var connected = null;

   var setContentPage = function(html) {
      $('#page_content').html(html);
      if (connected) {
         $('#page_content .connected').show();
         $('#page_content .not-connected').hide();
      } else {
         $('#page_content .connected').hide();
         $('#page_content .not-connected').show();
      }
   };

   var load = function(href) {
      href = href.replace(/^!/, '');

      $.ajax({
         url: href,
         success: setContentPage,
         complete: function() {
            // google analytics here.
         }
      });

   }

   $(document).ready(function() {
      Functional.install();

      $.history.init(function(url) {
         load(url == "" ? "/home" : url);
      },
      { unescape: "/,&!" });

      $('a.histolink').live('click', function(link) {
         var url = $(this).attr('href');
         url = url.replace(/^.*#/, '');
         $.history.load(url);
         return false;
      });

      $('#search-button').click(function(e) {
         e.preventDefault();
         var searchText = $('#search-input').val();
         LikedList.Instance('search-results', '@{Application.search()}', {limit:10,text: searchText}, '@{Reco.isLiked()}', '@{Reco.switchLike()}', '@{Reco.switchIgnore()}');
      });
      var recentsLiked = new LikedList.Instance('recents', '@{Application.lastAdded()}', {limit:10}, '@{Reco.isLiked()}', '@{Reco.switchLike()}', '@{Reco.switchIgnore()}');
      var popularsLiked = new LikedList.Instance('populars', '@{Application.mostLiked()}', {limit:10}, '@{Reco.isLiked()}', '@{Reco.switchLike()}', '@{Reco.switchIgnore()}');
      var relatedLiked = new LikedList.Instance('related', '@{Application.recommendFromLiked()}', {limit:10}, '@{Reco.isLiked()}', '@{Reco.switchLike()}', '@{Reco.switchIgnore()}');

      var onConnectedUser = function(username) {
         Login.withConnectedUserDefault(username);
         LikedList.Instance('user-recommendations', '@{Reco.recommendUser()}', {limit:10}, '@{Reco.isLiked()}', '@{Reco.switchLike()}', '@{Reco.switchIgnore()}');
         var likedAddBox = new LikedAddBox.Instance('add-liked', '@{Reco.addLiked()}');
         likedAddBox.onLikedAdded.add(recentsLiked.refresh);
         likedAddBox.onLikedAdded.add(popularsLiked.refresh);
      }

      recentsLiked.onClickLiked.add(relatedLiked.refresh);
      popularsLiked.onClickLiked.add(relatedLiked.refresh);
      relatedLiked.onClickLiked.add(relatedLiked.refresh);

      Login.init('@{Security.userName()}', onConnectedUser);

      FB.init({appId: '161437957254554', status: true, cookie: true, xfbml: true});
   });
});