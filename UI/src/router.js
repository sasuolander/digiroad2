(function (root) {
  root.URLRouter = function(map, backend, models) {

    var mapCenterAndZoom = function(x, y, zoom){
      var mapView = map.getView();
      mapView.setCenter([x, y]);
      mapView.setZoom(zoom);
    };

    function fetchLinearAssetEvent (asset, result) {
      eventbus.once(asset.multiElementEventCategory + ':fetched', function() {
        var linearAsset = asset.selectedLinearAsset.getLinearAsset(result.id);
        if (linearAsset) {
          asset.selectedLinearAsset.open(linearAsset, true);
          applicationModel.setSelectedTool('Select');
        }
      });
    }

    function fetchSpeedLimitEvent (asset, result) {
      eventbus.once('speedLimits:redrawed', function() {
        var speedLimit = asset.getSpeedLimitById(result.id);
        if (speedLimit) {
          eventbus.trigger('speedLimits:enableTrafficSigns');
          asset.open(speedLimit, true);
          applicationModel.setSelectedTool('Select');
        }
      });
    }

    var linearCentering = function(layerName, id){
      applicationModel.selectLayer(layerName);
      var asset = _(models.linearAssets).find({ layerName: layerName });
      var linearAsset = asset.selectedLinearAsset.getLinearAsset(parseInt(id));
      if (linearAsset) {
        asset.selectedLinearAsset.open(linearAsset, true);
        applicationModel.setSelectedTool('Select');
      }
      backend.getLinearAssetMidPoint(asset.typeId, id).then(function (result) {
        if(result.success){
          if(result.source === 1){
            fetchLinearAssetEvent(asset, result);
          }else if(result.source === 2) {
            eventbus.once(asset.multiElementEventCategory + ':fetched', function () {
              eventbus.trigger(layerName + ':activeComplementaryLayer');
              eventbus.trigger('complementaryLinks:show');
              fetchLinearAssetEvent(asset, result);
            });
          }
          mapCenterAndZoom(result.middlePoint.x, result.middlePoint.y, 12);
        }
      });
    };

    var pointCentering = function (layerName, id) {
      applicationModel.selectLayer(layerName);
      var asset = _(models.pointAssets).find({layerName: layerName});
      backend.getPointAssetById(id, layerName).then(function (result) {
        mapCenterAndZoom(result.lon, result.lat, 12);

        asset.selectedLinearAsset.open(result);
      });
    };

    var speedLimitCentering = function (layerName, id) {
      applicationModel.selectLayer(layerName);
      var asset = models.selectedSpeedLimit;
      var speedLimit = asset.getSpeedLimitById(parseInt(id));
      if (speedLimit) {
        asset.open(speedLimit, true);
        applicationModel.setSelectedTool('Select');
      }
      backend.getLinearAssetMidPoint(20, id).then(function (result) {
        if (result.success) {
          if (result.source === 1) {
            fetchSpeedLimitEvent(asset, result);
          } else if (result.source === 2) {
            eventbus.once(asset.multiElementEventCategory + ':fetched', function () {
              eventbus.trigger(layerName + ':activeComplementaryLayer');
              eventbus.trigger('complementaryLinks:show');
              fetchSpeedLimitEvent(asset, result);
            });
          }
          mapCenterAndZoom(result.middlePoint.x, result.middlePoint.y, 12);
        }
      });
    };

    var linearAssetMapCenterAndZoom  = function (layerName, idType, id) {
      if(idType) {
        applicationModel.selectLayer(layerName);
        backend.getRoadLinkByLinkId(id, function (response) {
          if (response.success)
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      } else
        linearCentering(layerName, id);
    };

    var pointAssetMapCenterAndZoom  = function (layerName, idType, id) {
      if(idType) {
        applicationModel.selectLayer(layerName);
        backend.getRoadLinkByLinkId(id, function (response) {
          if (response.success)
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      } else
        pointCentering(layerName, id);
    };

    var manoeuvreMapCenterAndZoom = function(linkId){
      applicationModel.selectLayer('manoeuvre');

      backend.getRoadLinkByLinkId(linkId, function (response) {
        eventbus.once('manoeuvres:fetched', function () {
          if (!_.isUndefined(models.selectedManoeuvreSource.getByLinkId(linkId)))
            models.selectedManoeuvreSource.open(linkId);
        });
        mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
      });
    };

    var getLinearAssetType = function(layerName) {
      return _(models.linearAssets).find({layerName: layerName}).typeId;
    };

    var Router = Backbone.Router.extend({
      initialize: function () {
        // Support legacy format for opening mass transit stop via ...#300289
        this.route(/^(\d+)$/, function (nationalId) {
          this.massTransitStop(nationalId);
        });

        this.route(/^([A-Za-z]+)$/, function (layer) {
          applicationModel.selectLayer(layer);
        });

        this.route(/^$/, function () {
          applicationModel.selectLayer('massTransitStop');
        });

        this.stateHistory = null;

      },

      routes: {
        'massTransitStop/:id': 'massTransitStop',
        'asset/:id': 'massTransitStop',
        'linkProperty/:linkId': 'linkProperty',
        'linkProperty/mml/:mmlId': 'linkPropertyByMml',
        'speedLimit/:linkId(/municipality/:municipalityId/:position)': 'speedLimit',
        'speedLimitErrors/:id': 'speedLimitErrors',
        'hazardousMaterialTransportProhibitionErrors(/:typeId)/:id': 'hazardousMaterialTransportProhibitionErrors',
        'manoeuvreErrors(/:typeId)/:id': 'manoeuvreErrors',
        'heightLimitErrors(/:typeId)/:id': 'heightLimitErrors',
        'bogieWeightErrors(/:typeId)/:id': 'bogieWeightErrors',
        'axleWeightLimitErrors(/:typeId)/:id': 'axleWeightLimitErrors',
        'lengthLimitErrors(/:typeId)/:id': 'lengthLimitErrors',
        'totalWeightLimitErrors(/:typeId)/:id': 'totalWeightLimitErrors',
        'trailerTruckWeightLimitErrors(/:typeId)/:id': 'trailerTruckWeightLimitErrors',
        'widthLimitErrors(/:typeId)/:id': 'widthLimitErrors',
        'pedestrianCrossingsErrors(/:typeId)/:id': 'pedestrianCrossingsErrors',

        'pedestrianCrossings/:id': 'pedestrianCrossings',
        'trafficLights/:id': 'trafficLights',
        'obstacles/:id': 'obstacles',
        'railwayCrossings/:id': 'railwayCrossings',
        'directionalTrafficSigns/:id': 'directionalTrafficSigns',
        'trafficSigns/:id': 'trafficSigns',
        'maintenanceRoad/:id': 'maintenanceRoad',
        'litRoad/:id': 'litRoad',
        'roadWidth/:id': 'roadWidth',
        'numberOfLanes/:id': 'numberOfLanes',
        'massTransitLanes/:id': 'massTransitLanes',
        'prohibition/:id': 'prohibition',
        'hazardousMaterialTransportProhibition/:id': 'hazardousMaterialTransportProhibition',
        'totalWeightLimit/:id': 'totalWeightLimit',
        'trailerTruckWeightLimit/:id': 'trailerTruckWeightLimit',
        'axleWeightLimit/:id': 'axleWeightLimit',
        'bogieWeightLimit/:id': 'bogieWeightLimit',
        'heightLimit/:id': 'heightLimit',
        'lengthLimit/:id': 'lengthLimit',
        'widthLimit/:id': 'widthLimit',
        'work-list/speedLimit': 'speedLimitWorkList',
        'work-list/speedLimit/state': 'speedLimitStateWorkList',
        'work-list/speedLimit/municipality(/:id)': 'speedLimitMunicipalitiesWorkList',
        'work-list/speedLimitErrors': 'speedLimitErrorsWorkList',

        'work-list/hazardousMaterialTransportProhibitionErrors': 'hazardousMaterialProhibitionErrorsWorkList',
        'work-list/manoeuvreErrors': 'manoeuvreErrorsWorkList',
        'work-list/heightLimitErrors': 'heightLimitErrorsWorkList',
        'work-list/bogieWeightErrors': 'bogieWeightErrorsWorkList',
        'work-list/lengthLimitErrors': 'lengthLimitErrorsWorkList',
        'work-list/axleWeightLimitErrors': 'axleWeightLimitErrorsWorkList',
        'work-list/totalWeightLimitErrors': 'totalWeightLimitErrorsWorkList',
        'work-list/trailerTruckWeightLimitErrors': 'trailerTruckWeightLimitErrorsWorkList',
        'work-list/widthLimitErrors': 'widthLimitErrorsWorkList',
        'work-list/pedestrianCrossingsErrors': 'pedestrianCrossingsErrorsWorkList',

        'work-list/linkProperty': 'linkPropertyWorkList',
        'work-list/massTransitStop': 'massTransitStopWorkList',
        'work-list/pedestrianCrossings': 'pedestrianCrossingWorkList',
        'work-list/trafficLights': 'trafficLightWorkList',
        'work-list/obstacles': 'obstacleWorkList',
        'work-list/railwayCrossings': 'railwayCrossingWorkList',
        'work-list/directionalTrafficSigns': 'directionalTrafficSignsWorkList',
        'work-list/trafficSigns': 'trafficSignWorkList',
        'work-list/maintenanceRoad': 'maintenanceRoadWorkList',
        'work-list/municipality': 'municipalityWorkList',
        'work-list/:layerName': 'unverifiedLinearAssetWorkList'
      },

      massTransitStop: function (id) {
        applicationModel.selectLayer('massTransitStop');
        backend.getMassTransitStopByNationalId(id, function (massTransitStop) {
          eventbus.once('massTransitStops:available', function () {
            models.selectedMassTransitStopModel.changeByExternalId(id);
          });
          mapCenterAndZoom(massTransitStop.lon, massTransitStop.lat, 12);
        });
      },

      linkProperty: function (linkId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByLinkId(linkId, function (response) {
          if (response.success) {
            if (response.source === 1) {
              eventbus.once('linkProperties:available', function () {
                models.selectedLinkProperty.open(response.id);
              });
            } else if (response.source === 2) {
              eventbus.once('linkProperties:available', function () {
                eventbus.trigger('roadLinkComplementaryCheckBox:check');
                eventbus.trigger('roadLinkComplementary:show');
                eventbus.once('linkProperties:available', function () {
                  models.selectedLinkProperty.open(response.id);
                });
              });
            }
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
          }
          else {
            //TODO might be nice to show error message for user if roadlink  applied to #linkProperty/ url does not exist
          }
        });
      },

      linkPropertyByMml: function (mmlId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByMmlId(mmlId, function (response) {
          eventbus.once('linkProperties:available', function () {
            models.selectedLinkProperty.open(response.id);
          });
          mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      },

      speedLimit: function (linkId, municipalityId,  position) {
        if(position)
          this.stateHistory = {municipality: Number(municipalityId), position: position};

        applicationModel.selectLayer('speedLimit');
        backend.getRoadLinkByLinkId(linkId, function (response) {
          eventbus.once('speedLimits:fetched', function () {
            var asset = models.selectedSpeedLimit.getSpeedLimit(response.id);
            models.selectedSpeedLimit.open(asset, true);
            models.selectedSpeedLimit.cancel();
          });
          mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      },

      pedestrianCrossings: function (id) {
        applicationModel.selectLayer('pedestrianCrossings');
        backend.getPointAssetById(id, 'pedestrianCrossings').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedPedestrianCrossing.open(result);
        });
      },

      trafficLights: function (id) {
        applicationModel.selectLayer('trafficLights');
        backend.getPointAssetById(id, 'trafficLights').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedTrafficLight.open(result);
        });
      },

      trafficSigns: function (id) {
        applicationModel.selectLayer('trafficSigns');
        backend.getPointAssetById(id, 'trafficSigns').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedTrafficSign.open(result);
        });
      },

      obstacles: function (id) {
        applicationModel.selectLayer('obstacles');
        backend.getPointAssetById(id, 'obstacles').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedObstacle.open(result);
        });
      },

      railwayCrossings: function (id) {
        applicationModel.selectLayer('railwayCrossings');
        backend.getPointAssetById(id, 'railwayCrossings').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedRailwayCrossing.open(result);
        });
      },

      directionalTrafficSigns: function (id) {
        applicationModel.selectLayer('directionalTrafficSigns');
        backend.getPointAssetById(id, 'directionalTrafficSigns').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedDirectionalTrafficSign.open(result);
        });
      },

      speedLimitWorkList: function () {
        eventbus.trigger('workList:select', 'speedLimit', backend.getUnknownLimits());
      },

      speedLimitStateWorkList: function () {
        eventbus.trigger('workList:select', 'speedLimit', backend.getUnknownLimitsState());
      },

      speedLimitMunicipalitiesWorkList: function (id) {
        if(id || this.stateHistory) {
          var municipalityId = id ? id : this.stateHistory.municipality;
          eventbus.trigger('speedLimitMunicipality:select', backend.getUnknownLimitsMunicipality(municipalityId), this.stateHistory);
        }
        else
          eventbus.trigger('municipalities:select', backend.getMunicipalitiesWithUnknowns());

        this.stateHistory = null;

      },

      speedLimitErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'speedLimitErrors', backend.getSpeedLimitErrors());
      },

      hazardousMaterialProhibitionErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'hazardousMaterialTransportProhibitionErrors', backend.getInaccurateAssets(getLinearAssetType('hazardousMaterialTransportProhibition')));
      },

      manoeuvreErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'manoeuvreErrors', backend.getInaccurateManoeuvre());
      },

      heightLimitErrorsWorkList: function () {
        var typeId = _(models.linearAssets).find({ layerName: 'heightLimit' }).typeId;
        eventbus.trigger('workList:select', 'heightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('heightLimit')));
      },

      lengthLimitErrorsWorkList: function(){
        var typeId = _(models.linearAssets).find({ layerName: 'lengthLimit' }).typeId;
        eventbus.trigger('workList:select', 'lengthLimitErrors', backend.getInaccurateAssets(getLinearAssetType('lengthLimit')));
      },

      bogieWeightErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'bogieWeightErrors', backend.getInaccurateAssets(getLinearAssetType('bogieWeight')));
      },

      axleWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'axleWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('axleWeightLimit')));
      },

      totalWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'totalWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('totalWeightLimit')));
      },

      trailerTruckWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'trailerTruckWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('trailerTruckWeightLimit')));
      },

      widthLimitErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'widthLimitErrors', backend.getInaccurateAssets(getLinearAssetType('widthLimit')));
      },

      pedestrianCrossingsErrorsWorkList: function () {
        eventbus.trigger('workList:select', 'pedestrianCrossingsErrors', backend.getInaccuratePointAssets());
      },

      linkPropertyWorkList: function () {
        eventbus.trigger('workList:select', 'linkProperty', backend.getIncompleteLinks());
      },

      massTransitStopWorkList: function () {
        eventbus.trigger('workList:select', 'massTransitStop', backend.getFloatingMassTransitStops());
      },

      pedestrianCrossingWorkList: function () {
        eventbus.trigger('workList:select', 'pedestrianCrossings', backend.getFloatinPedestrianCrossings());
      },

      trafficLightWorkList: function () {
        eventbus.trigger('workList:select', 'trafficLights', backend.getFloatingTrafficLights());
      },

      trafficSignWorkList: function () {
        eventbus.trigger('workList:select', 'trafficSigns', backend.getFloatingTrafficSigns());
      },

      obstacleWorkList: function () {
        eventbus.trigger('workList:select', 'obstacles', backend.getFloatingObstacles());
      },
      railwayCrossingWorkList: function () {
        eventbus.trigger('workList:select', 'railwayCrossings', backend.getFloatingRailwayCrossings());
      },

      directionalTrafficSignsWorkList: function () {
        eventbus.trigger('workList:select', 'directionalTrafficSigns', backend.getFloatingDirectionalTrafficSigns());
      },

      maintenanceRoadWorkList: function () {
        eventbus.trigger('workList:select', 'maintenanceRoad', backend.getUncheckedLinearAsset(290));
      },

      municipalityWorkList: function () {
        eventbus.trigger('municipality:select', backend.getUnverifiedMunicipalities());
      },

      speedLimitErrors: function (id) {
        speedLimitCentering('speedLimit', id);
      },

      hazardousMaterialTransportProhibitionErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('hazardousMaterialTransportProhibition', id, linkId);
      },

      manoeuvreErrors: function (id, linkId) {
        manoeuvreMapCenterAndZoom(linkId);
      },

      heightLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('heightLimit', id, linkId);
      },

      bogieWeightErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('bogieWeightLimit', id, linkId);
      },

      axleWeightLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('axleWeightLimit', id, linkId);
      },

      lengthLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('lengthLimit', id, linkId);
      },

      totalWeightLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('totalWeightLimit', id, linkId);
      },

      trailerTruckWeightLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('trailerTruckWeightLimit', id, linkId);
      },

      widthLimitErrors: function (id, linkId) {
        linearAssetMapCenterAndZoom('widthLimit', id, linkId);
      },

      pedestrianCrossingsErrors: function (idType, id) {
        pointAssetMapCenterAndZoom('pedestrianCrossings', idType, id);
      },

      maintenanceRoad: function (id) {
        linearCentering('maintenanceRoad', id);
      },

      litRoad: function (id) {
        linearCentering('litRoad', id);
      },

      roadWidth: function (id) {
        linearCentering('roadWidth', id);
      },

      numberOfLanes: function (id) {
        linearCentering('numberOfLanes', id);
      },

      massTransitLanes: function (id) {
        linearCentering('massTransitLanes', id);
      },

      prohibition: function (id) {
        linearCentering('prohibition', id);
      },

      hazardousMaterialTransportProhibition: function (id) {
        linearCentering('hazardousMaterialTransportProhibition', id);
      },

      totalWeightLimit: function (id) {
        linearCentering('totalWeightLimit', id);
      },

      trailerTruckWeightLimit: function (id) {
        linearCentering('trailerTruckWeightLimit', id);
      },

      axleWeightLimit: function (id) {
        linearCentering('axleWeightLimit', id);
      },

      bogieWeightLimit: function (id) {
        linearCentering('bogieWeightLimit', id);
      },

      heightLimit: function (id) {
        linearCentering('heightLimit', id);
      },

      lengthLimit: function (id) {
        linearCentering('lengthLimit', id);
      },

      widthLimit: function (id) {
        linearCentering('widthLimit', id);
      },

      unverifiedLinearAssetWorkList: function(layerName) {
        var typeId = _.find(models.linearAssets, function(assetSpec) { return assetSpec.layerName == layerName; }).typeId;
        eventbus.trigger('verificationList:select', layerName, backend.getUnverifiedLinearAssets(typeId));
      }
    });

    var router = new Router();

    // We need to restart the router history so that tests can reset
    // the application before each test.
    Backbone.history.stop();
    Backbone.history.start();

    eventbus.on('asset:closed', function () {
      router.navigate('massTransitStop');
    });

    eventbus.on('asset:fetched asset:created', function (asset) {
      router.navigate('massTransitStop/' + asset.nationalId);
    });

    // Focus to mass transit stop asset after national id search
    eventbus.on('nationalId:selected', function (result) {
      router.navigate('massTransitStop/' + result.nationalId, {trigger: true});
    });

    eventbus.on('linkProperties:unselected', function () {
      router.navigate('linkProperty');
    });

    eventbus.on('linkProperties:selected', function (linkProperty) {
      router.navigate('linkProperty/' + linkProperty.linkId);
    });

    eventbus.on('layer:selected', function (layer) {
      router.navigate(layer);
    });
  };
})(this);