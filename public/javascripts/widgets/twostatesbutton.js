require.def("widgets/twostatesbutton", ["jquery", "utils"], function($, Utils) {

   // share by all widgets instance.
   var eventsManager = new Array();

   var drawWidget = function(containerId, contentOK, contentKO, widgetState) {
      if (widgetState == true) {
         $('#' + containerId).html(contentOK);
         $('#' + containerId).fadeIn('fast');
      } else if (widgetState == false) {
         $('#' + containerId).html(contentKO);
         $('#' + containerId).fadeIn('fast');
      } else {
         //undefined state
         $('#' + containerId).hide();
         $('#' + containerId).html('undefined state');
      }
   }

   var getEvent = function(widgetId) {
      if (!eventsManager[widgetId]) {
         eventsManager[widgetId] = new Utils.Event();
      }
      return eventsManager[widgetId];
   }

   return {
      "Instance": function (containerId, contentOK, contentKO, switchResource, data, stateAttrName, initialState, name) {
         var self = this;
         var onBrotherChangeState = function(value) {
            if (value.id == myId) return;
            else {
               drawWidget(containerId, contentOK, contentKO, value.state);
            }
         }

         var switchButton = function(e) {
            e.preventDefault();
            var el = $(this);
            $.ajax({
               url: switchResource,
               data: data,
               success: onSuccessSwitch,
               error: function() {
                  drawWidget(containerId);
               }
            })
         };

         var onSuccessSwitch = function(responseObject) {
            var widgetState = responseObject[0][stateAttrName];
            drawWidget(containerId, contentOK, contentKO, widgetState);
            event.execute({id:myId, state:widgetState});
         };

         var event = getEvent(name);
         var myId = event.add(onBrotherChangeState);

         drawWidget(containerId, contentOK, contentKO, initialState);
         $('#' + containerId).click(switchButton);
      },

      "getEvent":getEvent
   };
})
