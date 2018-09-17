(function(root) {
  root.ServicePointForm = function() {
    PointAssetForm.call(this);
    var me = this;
    var pointAsset;
    var roadCollection;
    var applicationModel;
    var backend;
    var saveCondition;

    this.initialize = function(parameters) {
      pointAsset = parameters.pointAsset;
      roadCollection = parameters.roadCollection;
      applicationModel = parameters.applicationModel;
      backend = parameters.backend;
      saveCondition = parameters.saveCondition;
      me.bindEvents(parameters);
    };

    var serviceTypes = [
      { value: 12, label: 'Pysäköintialue' },
      { value: 15, label: 'Pysäköintitalo' },
      { value: 11, label: 'Rautatieasema' },
      { value: 16, label: 'Linja-autoasema' },
      { value: 8,  label: 'Lentokenttä' },
      { value: 9,  label: 'Laivaterminaali' },
      { value: 10, label: 'Taksiasema' },
      { value: 6,  label: 'Lepoalue' },
      { value: 4,  label: 'Tulli' },
      { value: 5,  label: 'Rajanylityspaikka' },
      { value: 13, label: 'Autojen lastausterminaali' },
      { value: 14, label: 'Kuorma-autojen pysäköintialue' },
      { value: 17, label: 'Sähköautojen latauspiste'}
    ];

    var commonServiceExtension = [
      {value: 1, label: 'Kattava varustelu'},
      {value: 2, label: 'Perusvarustelu'},
      {value: 3, label: 'Yksityinen palvelualue'},
      {value: 4, label: 'Ei tietoa'}
    ];

    var serviceTypeExtensions = {
      6: commonServiceExtension,
      12: commonServiceExtension,
      14: commonServiceExtension,
      11: [
        {value: 5, label: 'Merkittävä rautatieasema'},
        {value: 6, label: 'Vähäisempi rautatieasema'},
        {value: 7, label: 'Maanalainen/metroasema'}
      ]
    };

    this.bindEvents = function(parameters) {
      var rootElement = $('#feature-attributes');
      var typeId = parameters.pointAsset.typeId;
      var selectedAsset = parameters.pointAsset.selectedPointAsset;
      var collection  = parameters.pointAsset.collection;
      var layerName = parameters.pointAsset.layerName;
      var localizedTexts = parameters.pointAsset.formLabels;
      var authorizationPolicy = parameters.pointAsset.authorizationPolicy;
      new FeedbackDataTool(parameters.feedbackCollection, layerName, authorizationPolicy);

      eventbus.on('assetEnumeratedPropertyValues:fetched', function(event) {
        if(event.assetType === typeId)
          me.enumeratedPropertyValues = event.enumeratedPropertyValues;
      });

      backend.getAssetEnumeratedPropertyValues(typeId);

      eventbus.on('application:readOnly', function(readOnly) {
        if(applicationModel.getSelectedLayer() === layerName && (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
          me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || readOnly);
          if (isSingleService(selectedAsset)){
            rootElement.find('button.delete').hide();
          }
        }
      });

      eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
        if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
          me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
          me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || applicationModel.isReadOnly());
          rootElement.find('button#save-button').prop('disabled', true);
          rootElement.find('button#cancel-button').prop('disabled', false);
          if(isSingleService(selectedAsset)){
              rootElement.find('button.delete').hide();
          }
        }
      });

      eventbus.on(layerName + ':changed', function() {
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && saveCondition(selectedAsset)));
        rootElement.find('button#cancel-button').prop('disabled', !(selectedAsset.isDirty()));
      });

      eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
        rootElement.empty();
      });

      eventbus.on('layer:selected', function() {
        $('#information-content .form[data-layer-name="' + layerName +'"]').remove();
      });
    };

    this.renderValueElement = function(asset) {
      var services = _(asset.services)
        .sortBy('serviceType', 'id')
        .map(renderService)
        .join('');

      return '' +
        '    <div class="form-group editable form-service">' +
        '      <ul>' +
        services +
        renderNewServiceElement() +
        '      </ul>' +
        '    </div>';
    };

    this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
      var id = selectedAsset.getId();

      var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
      var header = '<header><span>' + title + '</span>' + me.renderButtons() + '</header>';
      var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection);
      var footer = '<footer>' + me.renderButtons() + '</footer>';

      rootElement.html(header + form + footer);

      rootElement.find('.form-service textarea').on('input change', function (event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {additionalInfo: $(event.currentTarget).val()})});
      });

      rootElement.find('.service-name').on('input change', function (event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {name: $(event.currentTarget).val()})});
      });

      rootElement.find('.service-parking-place-count').on('input change', function (event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {parkingPlaceCount: parseInt($(event.currentTarget).val(), 10)})});
      });

      rootElement.find('.form-service').on('change', '.select-service-type', function (event) {
        var newServiceType = parseInt($(event.currentTarget).val(), 10);
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        var services = modifyService(selectedAsset.get().services, serviceId, {serviceType: newServiceType, isAuthorityData: isAuthorityData(newServiceType)});
        selectedAsset.set({services: services});
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || applicationModel.isReadOnly());
        rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
        if(services.length < 2){
          rootElement.find('button.delete').hide();
        }
      });

      rootElement.find('.form-service').on('change', '.new-service select', function (event) {
        var newServiceType = parseInt($(event.currentTarget).val(), 10);
        var assetId = selectedAsset.getId();
        var services = selectedAsset.get().services;
        var generatedId = services.length;
        var newServices = services.concat({id: generatedId, assetId: assetId, serviceType: newServiceType, isAuthorityData: isAuthorityData(newServiceType)});
        selectedAsset.set({services: newServices});
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || applicationModel.isReadOnly());
        rootElement.find('.form-controls button').prop('disabled', !selectedAsset.isDirty());
        if(newServices.length < 2){
          rootElement.find('button.delete').hide();
        }
      });

      rootElement.on('click', 'button.delete', function (evt) {
        var existingService = $(evt.target).closest('.service-point');
        $(evt.target).parent().parent().remove();
        var serviceId =  parseInt(existingService.find('input[type="text"]').attr('data-service-id'), 10);
        var services = selectedAsset.get().services;
        var newServices = _.reject(services, { id: serviceId });
        if(newServices.length < 2){
          rootElement.find('button.delete').hide();
        }
        selectedAsset.set({ services: newServices });
      });

      rootElement.find('.form-service').on('change', '.select-service-type-extension', function(event) {
        var serviceId = parseInt($(event.currentTarget).data('service-id'), 10);
        var newTypeExtension = parseInt($(event.currentTarget).val(), 10);
        selectedAsset.set({services: modifyService(selectedAsset.get().services, serviceId, {typeExtension: newTypeExtension})});
      });

      rootElement.find('.pointasset button.save').on('click', function() {
        selectedAsset.save();
      });

      rootElement.find('.pointasset button.cancel').on('click', function() {
        selectedAsset.cancel();
      });
    };

    var renderService = function (service) {
      var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
        return $('<option>', {value: serviceType.value, selected: service.serviceType === serviceType.value, text: serviceType.label})[0].outerHTML;
      }).join('');

      var selectedServiceType = _.find(serviceTypes, { value: service.serviceType });
      var parkingPlaceElements = '' +
        '<label class="control-label">Pysäköintipaikkojen lukumäärä</label>' +
        '<p class="form-control-static">' + (service.parkingPlaceCount || '–') + '</p>' +
        '<input type="text" class="form-control service-parking-place-count" data-service-id="' + service.id + '" value="' + (service.parkingPlaceCount || '')  + '">';

      return '<li>' +
        '  <div class="form-group service-point editable">' +
        '  <div class="form-group">' +
        '      <button class="delete btn-delete">x</button>' +
        '      <h4 class="form-control-static"> ' + (selectedServiceType ? selectedServiceType.label : '') + '</h4>' +
        '      <select class="form-control select-service-type" style="display:none" data-service-id="' + service.id + '">  ' +
        '        <option disabled selected>Lisää tyyppi</option>' +
        serviceTypeLabelOptions +
        '      </select>' +
        '    </div>' +
        serviceTypeExtensionElements(service, serviceTypeExtensions) +
        '<div>' +
        '    <label class="control-label">Palvelun nimi</label>' +
        '    <p class="form-control-static">' + (service.name || '–') + '</p> '+
        '    <input type="text" class="form-control service-name" data-service-id="' + service.id + '" value="' + (service.name || '')  + '">' +
        '</div><div>' +
        '    <label class="control-label">Palvelun lisätieto</label>' +
        '    <p class="form-control-static">' + (service.additionalInfo || '–') + '</p>' +
        '    <textarea class="form-control large-input" data-service-id="' + service.id + '">' + (service.additionalInfo || '')  + '</textarea>' +
        '    <label class="control-label">Viranomaisdataa</label>' +
        '    <p class="form-control-readOnly">'+ (isAuthorityData(service.serviceType) ?  'Kyllä' : 'Ei') +'</p>' +
        '</div><div>' +
        (showParkingPlaceCount(selectedServiceType) ? parkingPlaceElements : '') +
        '</div></div>' +
        '</li>';
    };

    function checkTypeExtension(service, modifications)  {
      var serviceType = modifications.serviceType ? modifications.serviceType : service.serviceType;
      if(!serviceTypeExtensions[serviceType])
        delete service.typeExtension;
    }

    function showParkingPlaceCount(selectedServiceType) {
      return (selectedServiceType.value === 12 || selectedServiceType.value === 15 || selectedServiceType.value === 14);
    }

    function renderNewServiceElement() {
      var serviceTypeLabelOptions = _.map(serviceTypes, function(serviceType) {
        return $('<option>', {value: serviceType.value, text: serviceType.label})[0].outerHTML;
      }).join('');

      return '' +
        '<li><div class="form-group new-service">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää uusi palvelu</option>' +
        serviceTypeLabelOptions +
        '  </select>' +
        '</div></li>';
    }

    function serviceTypeExtensionElements(service, serviceTypeExtensions) {
      var extensions = serviceTypeExtensions[service.serviceType];
      if (extensions) {
        var extensionOptions = _.map(extensions, function(extension) {
          return $('<option>', {value: extension.value, text: extension.label, selected: extension.value === service.typeExtension})[0].outerHTML;
        }).join('');
        var currentExtensionType = _.find(extensions, {value: service.typeExtension});
        return '' +
          '<div><label class="control-label">Tarkenne</label>' +
          '<p class="form-control-static">' + (currentExtensionType ? currentExtensionType.label : '–') + '</p>' +
          '<select class="form-control select-service-type-extension" style="display:none" data-service-id="' + service.id + '">  ' +
          '  <option disabled selected>Lisää tarkenne</option>' +
          extensionOptions +
          '</select></div>';
      } else {
        return '';
      }
    }

    function modifyService(services, id, modifications) {
      return _.map(services, function(service) {
        if (service.id === id) {
          checkTypeExtension(service, modifications);
          return _.merge({}, service, modifications);
        }
        return service;
      });
    }

    function isSingleService(selectedAsset){
      return selectedAsset.get().services.length < 2;
    }

    function isAuthorityData(selectedServiceType) {
      return !(selectedServiceType === 10 || selectedServiceType === 17);
    }

  };
})(this);