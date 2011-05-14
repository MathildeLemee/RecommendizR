var connected = null;
require(["jquery","widgets/likedlist", "widgets/likedaddbox", "widgets/login",  "jquery.history", "openid-jquery", "functional"], function($, LikedList, LikedAddBox, Login) {

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
   });
});