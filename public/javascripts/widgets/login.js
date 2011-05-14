require.def("widgets/login", ["jquery"], function($) {

   var withConnectedUserDefault = function (username) {
      $('#username-welcome').html(username);
      $('.not-connected').hide();
      $('.connected').fadeIn("fast");
      connected = true;
      twttr.anywhere(function (T) {
         T.hovercards();
      });
   };

   var withoutConnectedUserDefault = function () {
      $('.connected').hide();
      $('.not-connected').fadeIn("fast");
      connected = false;
   };

   var onLogout = function(withoutConnectedUser, link) {
      link.preventDefault();
      var url = $(this).attr('href');
      $.ajax({
         url: url,
         success: withoutConnectedUser,
         error: function () {
            // TODO
         }
      });
      return false;
   };

   var initPage = function(securityResource, withConnectedUser, withoutConnectedUser) {
      if (!withConnectedUser) {
         withConnectedUser = withConnectedUserDefault;
      }
      if (!withoutConnectedUser) {
         withoutConnectedUser = withoutConnectedUserDefault
      }

      $.ajax({
         url: securityResource,
         success: withConnectedUser,
         error: withoutConnectedUser
      });

      $('a.logout').live('click', onLogout.curry(withoutConnectedUser));

      openid.init('openid_identifier');
      openid.setDemoMode(false);
   };

   return {
      "withoutConnectedUserDefault" : withoutConnectedUserDefault,

      "withConnectedUserDefault" : withConnectedUserDefault,

      "init" :initPage
   };

})