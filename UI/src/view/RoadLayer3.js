var RoadStyles = function() {

  var administrativeClassStyleRule = [
    new StyleRule().where('administrativeClass').is('Private').use({ strokeColor: '#0011bb' }),
    new StyleRule().where('administrativeClass').is('Municipality').use({ strokeColor: '#11bb00' }),
    new StyleRule().where('administrativeClass').is('State').use({ strokeColor: '#ff0000' }),
    new StyleRule().where('administrativeClass').is('Unknown').use({ strokeColor: '#888' })
  ];

  var styleProvider = new StyleRuleProvider({ stroke: {width: 6, opacity: 1, color: "#5eaedf" }});
  styleProvider.addRules(administrativeClassStyleRule);

  return {
    provider: styleProvider
  };
};

(function(root) {
  root.RoadLayer3 = function(map, roadCollection,styler) {
    var vectorLayer;
    var layerMinContentZoomLevels = {};
    var layerStyleProviders = {};
    var uiState = { zoomLevel: 9 };

    var vectorSource = new ol.source.Vector({
      //loader: function(extent, resolution, projection) {
      //  var zoom = Math.log(1024/resolution) / Math.log(2);
      //  //setZoomLevel(zoom);
      //
      //
      //  eventbus.once('roadLinks:fetched', function() {
      //    vectorSource.clear();
      //    var features = _.map(roadCollection.getAll(), function(roadLink) {
      //      var points = _.map(roadLink.points, function(point) {
      //        return [point.x, point.y];
      //      });
      //      return new ol.Feature(_.merge({},roadLink, { geometry: new ol.geom.LineString(points)}));
      //      //feature.roadLinkData = roadLink;
      //      //return feature;
      //    });
      //    vectorSource.addFeatures(features);
      //  });
      //
      //  roadCollection.fetch(extent.join(','), zoom);
      //},
      strategy: ol.loadingstrategy.bbox
    });

    var setZoomLevel = function(zoom){
      uiState.zoomLevel = zoom;
    };

    var clear = function() {
      vectorSource.clear();
    };

    var createRoadLinkFeature = function(roadLink){
      var points = _.map(roadLink.points, function(point) {
        return [point.x, point.y];
      });
      return new ol.Feature(_.merge({}, roadLink, { geometry: new ol.geom.LineString(points)}));
    };

    //TODO maybe it's a good idea to spearete draw road links and set zoom
    var drawRoadLinks = function(roadLinks, zoom) {
      setZoomLevel(zoom);
      eventbus.trigger('roadLinks:beforeDraw');
      vectorSource.clear();
      var features = _.map(roadLinks, function(roadLink) {
        return createRoadLinkFeature(roadLink);
      });
      usingLayerSpecificStyleProvider(function() {
        vectorSource.addFeatures(features);
      });
      eventbus.trigger('roadLinks:afterDraw', roadLinks);
    };

    var drawRoadLink = function(roadLink){
      var feature = createRoadLinkFeature(roadLink);
      usingLayerSpecificStyleProvider(function() {
        vectorSource.addFeatures(feature);
      });
    };

    var setLayerSpecificStyleProvider = function(layer, provider) {
      layerStyleProviders[layer] = provider;
    };

    var setLayerSpecificMinContentZoomLevel = function(layer, zoomLevel) {
      layerMinContentZoomLevels[layer] = zoomLevel;
    };

    function vectorLayerStyle(feature, resolution) {
      var styleProvider = stylesUndefined() ? (new RoadStyles()).provider : layerStyleProviders[applicationModel.getSelectedLayer()]();
      return styleProvider.getStyle(_.merge({}, feature.getProperties(), {zoomLevel: uiState.zoomLevel}), feature);
    }

    function stylesUndefined() {
      return _.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()]);
    }

    //TODO have a look how we remove styles
    var disableColorsOnRoadLayer = function() {
      if (stylesUndefined()) {
        //vectorLayer.styleMap.styles.default.rules = [];
      }
    };

    //TODO have this on the default style rules
    var changeRoadsWidthByZoomLevel = function() {
      //var featureStyle = styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      //var opacityIndex = featureStyle[0].stroke_.color_.lastIndexOf(", ");
      //featureStyle[0].stroke_.color_ = featureStyle[0].stroke_.color_.substring(0,opacityIndex) + ", 1)";
      //return featureStyle;
      //
      // if (stylesUndefined()) {
      //   var widthBase = 2 + (map.getView().getZoom() - minimumContentZoomLevel());
      //   var roadWidth = widthBase * widthBase;
      //   if (applicationModel.isRoadTypeShown()) {
      //     vectorLayer.setStyle({stroke: roadWidth});
      //   } else {
      //     vectorLayer.setStyle({stroke: roadWidth});
      //     vectorLayer.styleMap.styles.default.defaultStyle.strokeWidth = 5;
      //     vectorLayer.styleMap.styles.select.defaultStyle.strokeWidth = 7;
      //   }
      // }
    };

    var usingLayerSpecificStyleProvider = function(action) {
      if (!_.isUndefined(layerStyleProviders[applicationModel.getSelectedLayer()])) {
        // vectorLayer.style = layerStyleProviders[applicationModel.getSelectedLayer()]();
      }
      action();
    };

    //TODO have a look on the vectorlayer redraw if exists
    var toggleRoadType = function() {
      if (applicationModel.isRoadTypeShown()) {
        enableColorsOnRoadLayer();
      } else {
        disableColorsOnRoadLayer();
      }
      changeRoadsWidthByZoomLevel();
      usingLayerSpecificStyleProvider(function() {
        //TODO now the redraw will always happen after a source change
        //vectorLayer.redraw();
      });
    };

    var minimumContentZoomLevel = function() {
      if (!_.isUndefined(layerMinContentZoomLevels[applicationModel.getSelectedLayer()])) {
        return layerMinContentZoomLevels[applicationModel.getSelectedLayer()];
      }
      return zoomlevels.minZoomForRoadLinks;
    };

    var handleRoadsVisibility = function() {
      if (_.isObject(vectorLayer)) {
        vectorLayer.setVisible(map.getView().getZoom() >= minimumContentZoomLevel());
      }
    };

    var mapMovedHandler = function(mapState) {
      //TODO be sure about the less 2 in the zoom
      /*
      var zoom = mapState.zoom - 2;
      if (zoom !== uiState.zoomLevel) {
        uiState.zoomLevel = zoom;
        vectorSource.clear();
      }
*/
      // If zoom changes clear the road list
      // if (mapState.zoom >= minimumContentZoomLevel()) {
      //
      //   vectorLayer.setVisible(true);
      //   changeRoadsWidthByZoomLevel();
      // } else {
      //   vectorLayer.clear();
      //   roadCollection.reset();
      // }generateStyleByFeature
      handleRoadsVisibility();
    };

    vectorLayer = new ol.layer.Vector({
      source: vectorSource,
      style: vectorLayerStyle
    });
    vectorLayer.setVisible(true);
    map.addLayer(vectorLayer);

    eventbus.on('asset:saved asset:updateCancelled asset:updateFailed', function() {
      //TODO change this to use the new way to do selectcontrol
      //selectControl.unselectAll();
    }, this);

    eventbus.on('road-type:selected', toggleRoadType, this);

    eventbus.on('map:moved', mapMovedHandler, this);

    eventbus.on('layer:selected', function(layer) {
      //TODO have sure this works, i belive it will work because now the style is a funciton and we can get the style from the provider
      //activateLayerStyleMap(layer);
      toggleRoadType();
    }, this);

    return {
      setLayerSpecificMinContentZoomLevel: setLayerSpecificMinContentZoomLevel,
      setLayerSpecificStyleProvider: setLayerSpecificStyleProvider,
      drawRoadLink: drawRoadLink,
      drawRoadLinks: drawRoadLinks,
      clear: clear,
      layer: vectorLayer
    };
  };
})(this);
