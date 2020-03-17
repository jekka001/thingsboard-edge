/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
import React from 'react';
import { utils } from 'react-schema-form';

import ThingsboardArray from './json-form-array.jsx';
import ThingsboardJavaScript from './json-form-javascript.jsx';
import ThingsboardJson from './json-form-json.jsx';
import ThingsboardHtml from './json-form-html.jsx';
import ThingsboardCss from './json-form-css.jsx';
import ThingsboardColor from './json-form-color.jsx'
import ThingsboardRcSelect from './json-form-rc-select.jsx';
import ThingsboardNumber from './json-form-number.jsx';
import ThingsboardText from './json-form-text.jsx';
import Select from 'react-schema-form/lib/Select';
import Radios from 'react-schema-form/lib/Radios';
import ThingsboardDate from './json-form-date.jsx';
import ThingsboardImage from './json-form-image.jsx';
import ThingsboardCheckbox from './json-form-checkbox.jsx';
import Help from 'react-schema-form/lib/Help';
import ThingsboardFieldSet from './json-form-fieldset.jsx';
import ThingsboardIcon from './json-form-icon.jsx';

import _ from 'lodash';

class ThingsboardSchemaForm extends React.Component {

    constructor(props) {
        super(props);

        this.mapper = {
            'number': ThingsboardNumber,
            'text': ThingsboardText,
            'password': ThingsboardText,
            'textarea': ThingsboardText,
            'select': Select,
            'radios': Radios,
            'date': ThingsboardDate,
            'image': ThingsboardImage,
            'checkbox': ThingsboardCheckbox,
            'help': Help,
            'array': ThingsboardArray,
            'javascript': ThingsboardJavaScript,
            'json': ThingsboardJson,
            'html': ThingsboardHtml,
            'css': ThingsboardCss,
            'color': ThingsboardColor,
            'rc-select': ThingsboardRcSelect,
            'fieldset': ThingsboardFieldSet,
            'icon': ThingsboardIcon
        };

        this.onChange = this.onChange.bind(this);
        this.onColorClick = this.onColorClick.bind(this);
        this.onIconClick = this.onIconClick.bind(this);
        this.onToggleFullscreen = this.onToggleFullscreen.bind(this);
        this.hasConditions = false;
    }

    onChange(key, val) {
        //console.log('SchemaForm.onChange', key, val);
        this.props.onModelChange(key, val);
        if (this.hasConditions) {
            this.forceUpdate();
        }
    }

    onColorClick(event, key, val) {
        this.props.onColorClick(event, key, val);
    }

    onIconClick(event) {
        this.props.onIconClick(event);
    }

    onToggleFullscreen() {
        this.props.onToggleFullscreen();
    }

    
    builder(form, model, index, onChange, onColorClick, onIconClick, onToggleFullscreen, mapper) {
        var type = form.type;
        let Field = this.mapper[type];
        if(!Field) {
            console.log('Invalid field: \"' + form.key[0] + '\"!');
            return null;
        }
        if(form.condition) {
            this.hasConditions = true;
            if (eval(form.condition) === false) {
                return null;
            }
        }
        return <Field model={model} form={form} key={index} onChange={onChange} onColorClick={onColorClick} onIconClick={onIconClick} onToggleFullscreen={onToggleFullscreen} mapper={mapper} builder={this.builder}/>
    }

    createSchema(theForm) {
        let merged = utils.merge(this.props.schema, theForm, this.props.ignore, this.props.option);
        let mapper = this.mapper;
        if(this.props.mapper) {
            mapper = _.merge(this.mapper, this.props.mapper);
        }
        let forms = merged.map(function(form, index) {
            return this.builder(form, this.props.model, index, this.onChange, this.onColorClick, this.onIconClick, this.onToggleFullscreen, mapper);
        }.bind(this));

        let formClass = 'SchemaForm';
        if (this.props.isFullscreen) {
            formClass += ' SchemaFormFullscreen';
        }

        return (
            <div style={{width: '100%'}} className={formClass}>{forms}</div>
        );
    }

    render() {
        if(this.props.groupInfoes&&this.props.groupInfoes.length>0){
            let content=[];
            for(let info of this.props.groupInfoes){
                let forms = this.createSchema(this.props.form[info.formIndex]);
                let item = <ThingsboardSchemaGroup key={content.length} forms={forms} info={info}></ThingsboardSchemaGroup>;
                content.push(item);
            }
            return (<div>{content}</div>);
        }
        else
            return this.createSchema(this.props.form);
    }
}
export default ThingsboardSchemaForm;


class ThingsboardSchemaGroup extends React.Component{
    constructor(props) {
        super(props);
        this.state={
            showGroup:true
        }
    }

    toogleGroup(index) {
        this.setState({
            showGroup:!this.state.showGroup
        });
    }

    render() {
        let theCla = "pull-right fa fa-chevron-down md-toggle-icon"+(this.state.showGroup?"":" tb-toggled")
        return (<section className="md-whiteframe-z1" style={{marginTop: '10px'}}>
                    <div className='SchemaGroupname md-button-toggle' onClick={this.toogleGroup.bind(this)}>{this.props.info.GroupTitle}<span className={theCla}></span></div>
                    <div style={{padding: '20px'}} className={this.state.showGroup?"":"invisible"}>{this.props.forms}</div>
                </section>);
    }
} 
