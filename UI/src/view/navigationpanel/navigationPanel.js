(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, searchBox, layerSelectBox, assetControlGroups) {
    var navigationPanel = $('<div class="navigation-panel"></div>');

    navigationPanel.append(searchBox.element);
    navigationPanel.append(layerSelectBox.element);

    var assetControls = _.flatten(assetControlGroups);

    var assetElementDiv = $('<div class="asset-type-container"></div>');
    assetControls.forEach(function(asset) {
      assetElementDiv.append(asset.template());
    });
    navigationPanel.append(assetElementDiv);

    var assetControlMap = _.chain(assetControls)
      .map(function(asset) {
        return [asset.layerName, asset];
      })
      .fromPairs()
      .value();

    bindEvents();

/*    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      console.log('NavigationPanel layer:selected')
      console.log(assetControlMap);
      console.log(applicationModel.getLayers());
      var layers = applicationModel.getLayers()
      var previousLayers = layers[previouslySelectedLayer];
      if (previousLayers){
        previousLayers.hide();
      }
     // layers[layer].show(map);
      layers[layer].show();
    });*/

    eventbus.on('navigation:selected', function selectNavigation(selectedNavigation,previouslySelectedNavigation) {
console.log('NavigationPanel navigation:selected')
      var previousControl = assetControlMap[previouslySelectedNavigation];
      if (previousControl){
        previousControl.hide();
      }
      assetControlMap[selectedNavigation].show();
      assetElementDiv.show();
    })

    container.append(navigationPanel);

    function bindEvents() {
      layerSelectBox.button.on('click', function() {
        layerSelectBox.toggle();
      });

      $(document).on('click', function(evt) {
        var clickOutside = !$(evt.target).closest('.navigation-panel').length;
        if (clickOutside) {
          layerSelectBox.hide();
          assetElementDiv.show();
        }
      });

      $(document).keyup(function(evt) {
        if (evt.keyCode === 27) {
          layerSelectBox.hide();
          assetElementDiv.show();
        }
      });
    }
  }
})(this);
